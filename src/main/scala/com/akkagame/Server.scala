package com.akkagame

/**
  * server which bootstrap the service and expose connection to outside world
  */


  import akka.actor.ActorSystem
  import akka.http.scaladsl.Http
  import akka.stream.ActorMaterializer
  import com.akkagame.service.GameService

  import scala.io.StdIn

  object Server {

    def main(args: Array[String]) {
      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()
      // needed for the future map/flatmap in the end
      implicit val executionContext = system.dispatcher

      /*val requestHandler: HttpRequest => HttpResponse = {
        case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
          HttpResponse(entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            "<html><body>Hello world!</body></html>"))

        case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
          HttpResponse(entity = "PONG!")

        case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
          sys.error("BOOM!")

        case r: HttpRequest =>
          r.discardEntityBytes() // important to drain incoming HTTP Entity stream
          HttpResponse(404, entity = "Unknown resource!")
      }*/
      val gameService = new GameService()
      val bindingFuture = Http().bindAndHandle(gameService.websocketRoute, "localhost", 8080)
      println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
      StdIn.readLine() // let it run until user presses return
      bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done

    }

}
