import com.google.gson.Gson
import spark.Spark.get
import spark.Spark.port
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*

object CIBServer {

    const val buttonStyle = "padding:8pt; margin:0 auto; display: block"
    val gson = Gson()

    fun start(port: Int) {
        port(port)

        get("/cities") { req, res ->
            var html = LGLParser.updateAndGetData().keys
                .sorted()
                .map { "<button style=\"$buttonStyle\" onclick=\"location.href = '/city?c=$it';\">${it.replace("_", " ")}</button>" }
                .joinToString("<br>")

            html = "<body style=\"width:100%;padding:10pt\">$html</body>"

            html
        }

        get("/city") { req, res ->
            val city = req.queryParams("c")
//            println("${formatCurrentTime()}:   Requested city: $city")

            LGLParser.updateAndGetData()[city]?.let {
                """<div style="font-size: 40px">
                    <h2>${it.name.replace("_", " ")}</h2>
                    <h3>${it.toInfoString()}</h3>
                    Cases: ${it.caseNumber} <br>
                    CaseChange: ${it.caseChange} <br>
                    CasesPer100k: ${it.casesPer100k} <br>
                    CasesPast7Days: ${it.casesPast7Days} <br>
                    SevenDayIncidencePer100k: ${it.sevenDayIncidencePer100k}<br>
                    Deaths: ${it.deathCount} <br>
                    DeathsChange: ${it.deathsChange}
                    </div>
                """.trimIndent()
            } ?: "Not found"
        }

        get("/api/cities") { req, res ->
            res.type("application/json")
            gson.toJson(LGLParser.updateAndGetData().keys)
        }
        get("/api/city") { req, res ->
            val city = req.queryParams("c")
            res.type("application/json")
            gson.toJson(LGLParser.updateAndGetData()[city])
        }
    }
}