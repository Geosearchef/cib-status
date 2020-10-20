import java.net.URL
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.regex.Pattern

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
val datePattern = Pattern.compile(
    "publikationsDatum\\s=\\s\"(\\d+)\\.(\\d+)\\.(\\d+)\""
)

fun main(args: Array<String>) {
    if(args.isEmpty() || args[0] == "--help") {
        println("Obtains the recent Covid-19 stats from www.lgl.bayern.de\n" +
                    "--list          Lists all available cities\n" +
                    "--city [city]   dumps all data for the chosen city\n" +
                    "--info [city]   prints an info string e.g. for displaying in a status bar"
        )
        return
    }

    val html = URL(address).readText()
//    println(html)

    val dateMatcher = datePattern.matcher(html)

    if(!dateMatcher.find()) {
        println("Couldn't find date")
        return
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
            matcher.group(2).replace(".", "").toInt(),
            matcher.group(3).replace(".", "").trim(),
            matcher.group(4).replace(".", "").toDoubleComma(),
            matcher.group(5).replace(".", "").toInt(),
            matcher.group(6).replace(".", "").toDoubleComma(),
            matcher.group(7).replace(".", "").toInt(),
            matcher.group(8).trim()
        )
    }

    if(args[0] == "--list") {
        cities.keys.sorted().forEach { println(it) }
    } else if(args[0] == "--city" && args.size >= 2) {
        println("Date: ${date.get(Calendar.DAY_OF_MONTH)}.${date.get(Calendar.MONTH) + 1}.${date.get(Calendar.YEAR)}")
        println(cities.get(args[1]))
    } else if (args[0] == "--info" && args.size >= 2) {
        println(cities.get(args[1])?.toInfoString(date) ?: "Not found")
    }
}

data class Info(val name: String,
                val caseNumber: Int,
                val caseChange: String,
                val casesPer100k: Double,
                val casesPast7Days: Int,
                val sevenDayIncidencePer100k: Double,
                val deathCount: Int,
                val deathsChange: String) {

    fun toInfoString(calendar: Calendar) : String {
        return String.format("${toDayString(calendar)}: %.1f", sevenDayIncidencePer100k)
    }

}

private fun toDayString(calendar: Calendar) : String {
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