/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.errai.enterprise.rebind;

import java.util.List;

import org.jboss.errai.ioc.rebind.ioc.codegen.DefParameters;
import org.jboss.errai.ioc.rebind.ioc.codegen.Parameter;
import org.jboss.errai.ioc.rebind.ioc.codegen.Statement;
import org.jboss.errai.ioc.rebind.ioc.codegen.Variable;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.BlockBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.ClassStructureBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.ContextualStatementBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaClassFactory;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaMethod;
import org.jboss.errai.ioc.rebind.ioc.codegen.util.Bool;
import org.jboss.errai.ioc.rebind.ioc.codegen.util.Stmt;
import org.jboss.resteasy.specimpl.UriBuilderImpl;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;

/**
 * Generates a JAX-RS remote proxy method.
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class JaxrsProxyMethodGenerator {
  private static final String APPEND = "append";

  private String rootResourcePath;
  private JaxrsResourceMethod resourceMethod;
  
  private Statement errorHandling;
  private Statement responseHandling;

  public JaxrsProxyMethodGenerator(JaxrsResourceMethod resourceMethod, String rootResourcePath) {
    this.resourceMethod = resourceMethod;
    this.rootResourcePath = rootResourcePath;
  }

  public void generate(ClassStructureBuilder<?> classBuilder) {
    errorHandling = Stmt.loadStatic(classBuilder.getClassDefinition(), "this")
      .invoke("handleError", Variable.get("throwable"));
    responseHandling = Stmt.loadStatic(classBuilder.getClassDefinition(), "this")
      .invoke("handleResponse", Variable.get("response"));
    
    MetaMethod method = resourceMethod.getMethod();

    BlockBuilder<?> methodBlock =
        classBuilder.publicMethod(method.getReturnType(), method.getName(),
            DefParameters.from(method).getParameters().toArray(new Parameter[0]));

    if (resourceMethod.getHttpMethod() != null) {
      generatePath(methodBlock);
      generateRequestBuilder(methodBlock);
      generateRequest(methodBlock);
    }

    if (!method.getReturnType().equals(MetaClassFactory.get(void.class)))
      methodBlock.append(Stmt.load(null).returnValue());

    methodBlock.finish();
  }

  private void generatePath(BlockBuilder<?> methodBlock) {
    String path = rootResourcePath + resourceMethod.getPath();
    List<String> pathParams =
        ((UriBuilderImpl) UriBuilderImpl.fromTemplate(path)).getPathParamNamesInDeclarationOrder();

    ContextualStatementBuilder pathValue = Stmt.loadLiteral(path);
    for (String pathParam : pathParams) {
      pathValue = pathValue.invoke("replaceFirst", "\\{" + pathParam + "\\}",
          Variable.get(resourceMethod.getPathParameter(pathParam)));
    }

    ContextualStatementBuilder pathBuilder = null;
    if (!resourceMethod.getQueryParameters().isEmpty())
      pathBuilder = Stmt.loadVariable("path").invoke(APPEND, "?");

    int i = 0;
    for (String queryParamKey : resourceMethod.getQueryParameters().keySet()) {
      for (String queryParam : resourceMethod.getQueryParameters().get(queryParamKey)) {
        pathBuilder = pathBuilder.invoke(APPEND, queryParamKey);
        pathBuilder = pathBuilder.invoke(APPEND, "=");
        pathBuilder = pathBuilder.invoke(APPEND, Variable.get(queryParam));
        if (++i < resourceMethod.getNumberOfQueryParams())
          pathBuilder = pathBuilder.invoke(APPEND, "&");
      }
    }

    methodBlock.append(Stmt.declareVariable("path", StringBuilder.class,
        Stmt.newObject(StringBuilder.class).withParameters(pathValue)));

    if (pathBuilder != null)
      methodBlock.append(pathBuilder);
  }

  private void generateRequestBuilder(BlockBuilder<?> methodBlock) {
    Statement urlEncoder = Stmt.invokeStatic(URL.class, "encode", Stmt.loadVariable("path").invoke("toString"));

    Statement requestBuilder =
        Stmt.declareVariable("requestBuilder", RequestBuilder.class,
            Stmt.newObject(RequestBuilder.class)
                .withParameters(resourceMethod.getHttpMethod(), urlEncoder));

    methodBlock.append(requestBuilder);
  }

  private void generateRequest(BlockBuilder<?> methodBlock) {
    ContextualStatementBuilder sendRequest = Stmt.loadVariable("requestBuilder");
    if (resourceMethod.getEntityParameterName() == null) {
      sendRequest = sendRequest.invoke("sendRequest", null, generateRequestCallback());
    }
    else {
      Statement body = Variable.get(resourceMethod.getEntityParameterName());
      sendRequest = sendRequest.invoke("sendRequest", body, generateRequestCallback());
    }

    methodBlock.append(Stmt
        .try_()
        .append(sendRequest)
        .finish()
        .catch_(RequestException.class, "throwable")
        // TODO separate error callback for JAX-RS rpcs?
        .append(errorHandling)
        .finish());
  }

  private Statement generateRequestCallback() {
    Statement requestCallback = Stmt
        .newObject(RequestCallback.class)
        .extend()
        .publicOverridesMethod("onError", Parameter.of(Request.class, "request"),
            Parameter.of(Throwable.class, "throwable"))
        .append(errorHandling)
        .finish()
        .publicOverridesMethod("onResponseReceived", Parameter.of(Request.class, "request"),
            Parameter.of(Response.class, "response"))
        .append(Stmt.if_(
            Bool.and(
                Bool.greaterThanOrEqual(Stmt.loadVariable("response").invoke("getStatusCode"), 200),
                Bool.lessThan(Stmt.loadVariable("response").invoke("getStatusCode"), 300)))
            .append(responseHandling)
            .finish()
            .else_()
            .append(Stmt.declareVariable("throwable", RequestException.class,
                 Stmt.newObject(RequestException.class).withParameters(
                     Stmt.invokeStatic(Integer.class, "toString",
                         Stmt.loadVariable("response").invoke("getStatusCode")))))
            .append(errorHandling)
            .finish())
        .finish()
        .finish();

    return requestCallback;
  }
}