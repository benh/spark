/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spark.ui

import javax.servlet.http.HttpServletRequest

import org.eclipse.jetty.server.{Handler, Server}

import spark.{Logging, SparkContext, Utils}
import spark.ui.env.EnvironmentUI
import spark.ui.exec.ExecutorsUI
import spark.ui.storage.BlockManagerUI
import spark.ui.jobs.JobProgressUI
import spark.ui.JettyUtils._

/** Top level user interface for Spark */
private[spark] class SparkUI(sc: SparkContext) extends Logging {
  val host = Utils.localHostName()
  val port = Option(System.getProperty("spark.ui.port")).getOrElse(SparkUI.DEFAULT_PORT).toInt
  var boundPort: Option[Int] = None
  var server: Option[Server] = None

  val handlers = Seq[(String, Handler)](
    ("/static", createStaticHandler(SparkUI.STATIC_RESOURCE_DIR)),
    ("/", createRedirectHandler("/stages"))
  )
  val storage = new BlockManagerUI(sc)
  val jobs = new JobProgressUI(sc)
  val env = new EnvironmentUI(sc)
  val exec = new ExecutorsUI(sc)
  val allHandlers = storage.getHandlers ++ jobs.getHandlers ++ env.getHandlers ++
    exec.getHandlers ++ handlers

  /** Bind the HTTP server which backs this web interface */
  def bind() {
    try {
      val (srv, usedPort) = JettyUtils.startJettyServer("0.0.0.0", port, allHandlers)
      logInfo("Started Spark Web UI at http://%s:%d".format(host, usedPort))
      server = Some(srv)
      boundPort = Some(usedPort)
    } catch {
    case e: Exception =>
      logError("Failed to create Spark JettyUtils", e)
      System.exit(1)
    }
  }

  /** Initialize all components of the server */
  def start() {
    // NOTE: This is decoupled from bind() because of the following dependency cycle:
    //  DAGScheduler() requires that the port of this server is known
    //  This server must register all handlers, including JobProgressUI, before binding
    //  JobProgressUI registers a listener with SparkContext, which requires sc to initialize
    jobs.start()
    exec.start()
  }

  def stop() {
    server.foreach(_.stop())
  }

  private[spark] def appUIAddress = "http://" + host + ":" + boundPort.getOrElse("-1")
}

private[spark] object SparkUI {
  val DEFAULT_PORT = "33000"
  val STATIC_RESOURCE_DIR = "spark/ui/static"
}
