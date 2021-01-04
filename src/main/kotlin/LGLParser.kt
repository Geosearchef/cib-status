import java.net.URL
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

object LGLParser {

    const val address = "https://www.lgl.bayern.de/gesundheit/infektionsschutz/infektionskrankheiten_a_z/coronavirus/karte_coronavirus/"

//<tr>
//<td>München Stadt</td>
//<td>
//13.665                                </td>
//<td>
//(+ 97)                                </td>
//<td>920,68                                </td>
//<td>1.075</td>
//<td>72,43                                </td>
//<td>
//236                                </td>
//<td>
//-                                </td>
//</tr>

    val cityPattern = Pattern.compile(
        "<td>(.+)</td>\\s*" +
                "<td>\\s*([\\d,\\.]+)\\s*</td>\\s*" +
                "<td>\\s*(.+)\\s*</td>\\s*" +
                "<td>\\s*([\\d,\\.]+)\\s*</td>\\s*" +
                "<td>\\s*([\\d,\\.]+)\\s*</td>\\s*" +
                "<td>\\s*([\\d,\\.]+)\\s*</td>\\s*" +
                "<td>\\s*([\\d,\\.]+)\\s*</td>\\s*" +
                "<td>\\s*(.+)\\s*</td>"
    )
    val datePattern = Pattern.compile("publikationsDatum\\s=\\s[\"'](\\d+)\\.(\\d+)\\.(\\d+)[\"']")

    fun parseData(): HashMap<String, Info> {
        val html = URL(address).readText()

        val dateMatcher = datePattern.matcher(html)

        if(!dateMatcher.find()) {
            println("Couldn't find date")
            throw InputMismatchException("Couldn't find date")
        }

        var date = Calendar.getInstance().apply {
            set(Calendar.YEAR,  dateMatcher.group(3).toInt())
            set(Calendar.MONTH, dateMatcher.group(2).toInt() - 1)
            set(Calendar.DAY_OF_MONTH, dateMatcher.group(1).toInt())
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
//    date = GregorianCalendar.from(ZonedDateTime.ofInstant(date.toInstant().minus(Duration.ofDays(1)), ZoneId.systemDefault()))

        val matcher = cityPattern.matcher(html)

        val cities = HashMap<String, Info>()

        while(matcher.find()) {
            val name = matcher.group(1).replace(" ", "_")
            cities[name] = Info(
                name,
                date,
                matcher.group(2).replace(".", "").toInt(),
                matcher.group(3).replace(".", "").trim(),
                matcher.group(4).replace(".", "").toDoubleComma(),
                matcher.group(5).replace(".", "").toInt(),
                matcher.group(6).replace(".", "").toDoubleComma(),
                matcher.group(7).replace(".", "").toInt(),
                matcher.group(8).trim()
            )
        }

        return cities
    }

    val updateInterval = Duration.ofMinutes(10)
    var cities: HashMap<String, LGLParser.Info>? = null
    var lastUpdateTime: Instant = Instant.now().minus(Duration.ofDays(1))

    @Synchronized
    fun updateAndGetData(): HashMap<String, Info> {
        if(Instant.now().isAfter(lastUpdateTime.plus(updateInterval))) {
            lastUpdateTime = Instant.now()

            println(SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(Calendar.getInstance().time) + ":   Fetching data...")
            cities = LGLParser.parseData()
            println(formatCurrentTime() + ":   Fetch complete. ${cities?.size ?: 0} cities updated.")
        }

        return cities!!
    }


    data class Info(val name: String,
                    @Transient val date: Calendar,
                    val caseNumber: Int,
                    val caseChange: String,
                    val casesPer100k: Double,
                    val casesPast7Days: Int,
                    val sevenDayIncidencePer100k: Double,
                    val deathCount: Int,
                    val deathsChange: String) {

        val dayString = toDayString(date)

        fun toInfoString() : String {
            return String.format("${toDayString(date)}: %.1f", sevenDayIncidencePer100k)
        }

    }

    public fun toDayString(calendar: Calendar) : String {
        return when(calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Mo"
            Calendar.TUESDAY -> "Di"
            Calendar.WEDNESDAY -> "Mi"
            Calendar.THURSDAY -> "Do"
            Calendar.FRIDAY -> "Fr"
            Calendar.SATURDAY -> "Sa"
            Calendar.SUNDAY -> "So"
            else -> "?"
        }
    }


    private fun String.toDoubleComma() = this.replace(",", ".").toDouble()
}