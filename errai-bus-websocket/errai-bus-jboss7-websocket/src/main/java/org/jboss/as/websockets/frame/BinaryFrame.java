/*
 * Copyright (C) 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.websockets.frame;

import org.jboss.as.websockets.FrameType;

/**
 * Represents a binary WebSocket frame.
 *
 * @author Mike Brock
 */
public class BinaryFrame extends AbstractFrame {
  private final byte[] data;

  private BinaryFrame(byte[] data) {
    super(FrameType.Binary);
    this.data = data;
  }

  public static BinaryFrame from(byte[] data) {
    return new BinaryFrame(data);
  }

  public byte[] getByteArray() {
    return data;
  }
}
