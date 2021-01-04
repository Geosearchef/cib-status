import LGLParser.toDayString
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.HashMap

object RKIParser {

    const val WEBPAGE_URL = "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Impfquotenmonitoring.xlsx"
    const val TABLE_URL = "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Impfquotenmonitoring.xlsx?__blob=publicationFile"

    // this should be an enum
    const val COLUMN_STATE = "Bundesland"
    const val COLUMN_TOTAL_VACC = "Impfungen kumulativ"
    const val COLUMN_CHANGE_VACC = "Differenz zum Vortag"
    const val COLUMN_COUNT_AGED = "nach Alter"
    const val COLUMN_COUNT_JOB = "Beruflich"
    const val COLUMN_COUNT_MEDICAL = "Medizinisch"
    const val COLUMN_COUNT_NURSING_HOME = "Pflegeheim"
    val COLUMN_NAMES = listOf(COLUMN_STATE, COLUMN_TOTAL_VACC, COLUMN_CHANGE_VACC, COLUMN_COUNT_AGED, COLUMN_COUNT_JOB, COLUMN_COUNT_MEDICAL, COLUMN_COUNT_NURSING_HOME)

    var populations = mapOf(
        "Baden-Württemberg" to 11100394,
        "Bayern" to 13124737,
        "Berlin" to 3669491,
        "Brandenburg" to 2521893,
        "Bremen" to 681202,
        "Hamburg" to 1847253,
        "Hessen" to 6288080,
        "Mecklenburg-Vorpommern" to 1608138,
        "Niedersachsen" to 7993608,
        "Nordrhein-Westfalen" to 17947221,
        "Rheinland-Pfalz" to 4093903,
        "Saarland" to 986887,
        "Sachsen" to 4071971,
        "Sachsen-Anhalt" to 2194782,
        "Schleswig-Holstein" to 2903773,
        "Thüringen" to 2133378,
        "Deutschland" to 83166711
    )

    val datePattern = Pattern.compile("Datenstand (\\d+)\\.(\\d+)\\.(\\d+)")

    fun parseData(): HashMap<String, StateVaccinationInfo> {

        val html = URL(WEBPAGE_URL).readText()

        val dateMatcher = datePattern.matcher(html)
        var date: Calendar

        if(dateMatcher.find()) {
            date = Calendar.getInstance().apply {
                set(Calendar.YEAR,  dateMatcher.group(3).toInt())
                set(Calendar.MONTH, dateMatcher.group(2).toInt() - 1)
                set(Calendar.DAY_OF_MONTH, dateMatcher.group(1).toInt())
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
        } else {
            println("Couldn't find vaccination status date")
            date = Calendar.getInstance() // today
        }

        // Download table
        val tableFile = Paths.get("vaccinations.tmp.xlsx")
        Files.write(tableFile, URL(TABLE_URL).readBytes())

        val result: HashMap<String, StateVaccinationInfo> = HashMap()

        // Analyze table
        val workbook = XSSFWorkbook(Files.newInputStream(tableFile))
        val sheet = workbook.sheetIterator().asSequence().find { it.getRow(0).getCell(0).stringCellValue.equals("Bundesland") }
        if(sheet == null) {
            println("Sheet not found")
            return result
        }
        println("Using sheet ${sheet.sheetName}")

        val rowIter = sheet.rowIterator()
        val firstRow = rowIter.next()

        // find indices for desired columns
        val indicesMap = HashMap<String, Int>()
        for(cellIndex in 0 until 30) {
            val cellString = firstRow.getCell(cellIndex)?.stringCellValue ?: continue
            COLUMN_NAMES.filter { cellString.contains(it)}.forEach { indicesMap[it] = cellIndex }
        }

        while(rowIter.hasNext()) {
            val row = rowIter.next()

            if(row.getCell(1).cellType != CellType.NUMERIC || row.getCell(0).stringCellValue.equals("Gesamt")) {
                break
            }

            val stateInfo = StateVaccinationInfo(
                indicesMap[COLUMN_STATE]?.let { row.getCell(it).stringCellValue } ?: "unknown",
                date,
                indicesMap[COLUMN_TOTAL_VACC]?.let { row.getCell(it).numericCellValue.toInt() } ?: 0,
                indicesMap[COLUMN_CHANGE_VACC]?.let { row.getCell(it).numericCellValue.toInt() } ?: 0,
                indicesMap[COLUMN_COUNT_AGED]?.let { row.getCell(it).numericCellValue.toInt() } ?: 0,
                indicesMap[COLUMN_COUNT_JOB]?.let { row.getCell(it).numericCellValue.toInt() } ?: 0,
                indicesMap[COLUMN_COUNT_MEDICAL]?.let { row.getCell(it).numericCellValue.toInt() } ?: 0,
                indicesMap[COLUMN_COUNT_NURSING_HOME]?.let { row.getCell(it).numericCellValue.toInt() } ?: 0
            )

            result[stateInfo.stateName] = stateInfo
        }

        check(populations.values.sum() == populations["Deutschland"]!! * 2) // contains itself

        result["Deutschland"] = StateVaccinationInfo(
            "Deutschland",
            date,
            result.values.sumBy { it.count },
            result.values.sumBy { it.countChange },
            result.values.sumBy { it.countAged },
            result.values.sumBy { it.countJob },
            result.values.sumBy { it.countMedical },
            result.values.sumBy { it.countNursingHome }
        )

        return result
    }


    val updateInterval = Duration.ofMinutes(30)
    var lastUpdateTime: Instant = Instant.now().minus(Duration.ofDays(1))
    //TODO: data
    private var stateVaccinationInfos: HashMap<String, StateVaccinationInfo>? = null

    @Synchronized
    fun updateAndGetData(): HashMap<String, StateVaccinationInfo> {
        if(Instant.now().isAfter(lastUpdateTime.plus(updateInterval))) {
            lastUpdateTime = Instant.now()

            println(SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(Calendar.getInstance().time) + ":   Fetching vaccination data...")
            stateVaccinationInfos = RKIParser.parseData()
            println(formatCurrentTime() + ":   Vaccination fetch complete. ${stateVaccinationInfos?.size ?: 0} states updated.")
        }

        return stateVaccinationInfos!!
    }

    data class StateVaccinationInfo(
        val stateName: String,
        @Transient val date: Calendar,
        val count: Int,
        val countChange: Int,
        val countAged: Int,
        val countJob: Int,
        val countMedical: Int,
        val countNursingHome: Int
    ) {
        fun toInfoString() : String {
            return String.format("${toDayString(date)}: %.2f%%, %d (+%d)", count.toDouble() / population.toDouble() * 100.0, count, countChange)
        }

        val population = populations[stateName]!!
        val dayString = LGLParser.toDayString(date)
    }
}