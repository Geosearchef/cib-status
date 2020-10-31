import java.text.SimpleDateFormat
import java.util.*

fun main(args: Array<String>) {
    if(args.isEmpty() || args[0] == "--help") {
        println("Obtains the recent Covid-19 stats from www.lgl.bayern.de\n" +
                    "--list          Lists all available cities\n" +
                    "--city [city]   dumps all data for the chosen city\n" +
                    "--info [city]   prints an info string e.g. for displaying in a status bar\n" +
                    "--server [port] runs an http server exposing the data via an api"
        )
        return
    }

    if(args[0] == "--server") {
        if(args.size < 2) {
            println("Missing port")
            return
        }
        server(args[1].toInt())
    } else {
        standalone(args)
    }
}

fun standalone(args: Array<String>) {
    val cities = LGLParser.parseData()

    if(args[0] == "--list") {
        cities.keys.sorted().forEach { println(it) }
    } else if(args[0] == "--city" && args.size >= 2) {
        val city = cities[args[1]]!!
        println("Date: ${city.date.get(Calendar.DAY_OF_MONTH)}.${city.date.get(Calendar.MONTH) + 1}.${city.date.get(Calendar.YEAR)}")
        println(city)
    } else if (args[0] == "--info" && args.size >= 2) {
        println(cities[args[1]]?.toInfoString() ?: "Not found")
    }
}

fun server(port: Int) {
    CIBServer.start(port)
}

fun formatCurrentTime() = SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(Calendar.getInstance().time)