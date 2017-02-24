/**
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied.  See the License for the
  * specific language governing permissions and limitations
  * under the License.
  */
package com.sumologic.shellbot

import java.io.{ByteArrayOutputStream, OutputStream}

import akka.event.EventStream
import com.sumologic.shellbot.model.OutputBytes
import com.sumologic.sumobot.core.model.IncomingMessage

/**
  * OutputStream that publishes the bytes every time it's flushed.
  */
class ThreadPrinter(message: IncomingMessage, eventStream: EventStream) extends OutputStream {
  private val bos = new ByteArrayOutputStream()
  override def write(b: Int): Unit = {
    bos.write(b)
  }

  override def flush(): Unit = {
    eventStream.publish(OutputBytes(message, bos.toByteArray))
    bos.reset()
  }
}