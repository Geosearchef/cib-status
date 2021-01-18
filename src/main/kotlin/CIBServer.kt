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

        get("/states") { req, res ->
            var html = RKIParser.updateAndGetData().keys.filter { it != "Deutschland" }
                .sorted()
                .map { "<button style=\"$buttonStyle\" onclick=\"location.href = '/state?s=$it';\">${it.replace("_", " ")}</button>" }
                .joinToString("<br>")

            html =
                """<body style="width:100%;padding:10pt">
                    <h1 style="text-align: center">Impfungen</h1>
                    <div><button style="$buttonStyle" onclick="location.href = '/state?s=Deutschland';"><b>Gesamt</b</button></div><br><br>                    
                    $html
                    </body>""".trimIndent()

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

        get("/state") { req, res ->
            val state = req.queryParams("s")
//            println("${formatCurrentTime()}:   Requested city: $city")

            RKIParser.updateAndGetData()[state]?.let {
                """<div style="font-size: 40px">
                    <h2>${it.stateName}</h2>
                    <h3>1. ${it.toInfoString()}</h3>
                    <h3>2. ${it.toInfoStringSecond()}</h3>
                    
                    <br><h4>Erstimpfung</h4>
                    Impfungen: ${it.count} <br>
                    Anteil Gesamtbevölkerung: ${String.format("%.2f", it.count.toFloat() / it.population.toFloat() * 100.0)} % <br>
                    <br>
                    BioNTech: ${it.countBioNTech}<br>
                    Moderna: ${it.countModerna}<br>
                    
                    Differenz zum Vortag: ${it.countChange} <br><br>
                    Indikation nach Alter: ${it.countAged} <br>
                    Berufliche Indikation: ${it.countJob} <br>
                    Medizinische Indikation: ${it.countMedical}<br>
                    PflegeheimbewohnerIn: ${it.countNursingHome} <br>
                    
                    <br><h4>Zweitimpfung</h4>
                    Impfungen: ${it.count_2} <br>
                    Anteil Gesamtbevölkerung: ${String.format("%.2f", it.count_2.toFloat() / it.population.toFloat() * 100.0)} % <br>
                    Differenz zum Vortag: ${it.countChange_2} <br><br>
                    Indikation nach Alter: ${it.countAged_2} <br>
                    Berufliche Indikation: ${it.countJob_2} <br>
                    Medizinische Indikation: ${it.countMedical_2}<br>
                    PflegeheimbewohnerIn: ${it.countNursingHome_2} <br>
                    
                    <br>
                    <button style="$buttonStyle" onclick="location.href = '/states';">Back</button>
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
        get("/api/states") { req, res ->
            res.type("application/json")
            gson.toJson(RKIParser.updateAndGetData().keys)
        }
        get("/api/state") { req, res ->
            val state = req.queryParams("s")
            res.type("application/json")
            gson.toJson(RKIParser.updateAndGetData()[state])
        }
    }


}