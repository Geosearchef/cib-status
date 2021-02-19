import LGLParser.toDayString
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
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
    const val COLUMN_COUNT_BIONTECH = "BioNTech"
    const val COLUMN_COUNT_MODERNA = "Moderna"
    const val COLUMN_COUNT_ASTRAZENECA = "AstraZeneca"
    val COLUMN_NAMES = listOf(COLUMN_STATE, COLUMN_TOTAL_VACC, COLUMN_CHANGE_VACC, COLUMN_COUNT_AGED, COLUMN_COUNT_JOB, COLUMN_COUNT_MEDICAL, COLUMN_COUNT_NURSING_HOME, COLUMN_COUNT_BIONTECH, COLUMN_COUNT_MODERNA, COLUMN_COUNT_ASTRAZENECA)

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


    val columnLocations = HashMap<String, MutableList<DataColumnLocation>>()

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
        val sheetSum = workbook.sheetIterator().asSequence().find { it.sheetName.contains("Gesamt") && isDataSheet(it) }
        val sheetIndications = workbook.sheetIterator().asSequence().find { it.sheetName.contains("Indik") && isDataSheet(it) }
        if(sheetSum == null || sheetIndications == null) {
            println("Sheet not found")
            return result
        }
        println("Using sheet 1: ${sheetSum.sheetName}")
        println("Using sheet 2: ${sheetIndications.sheetName}")

        val headerRowsToScan = LinkedList<Row>()

        val rowIter = sheetSum.rowIterator()
        val rowIterInd = sheetIndications.rowIterator()

        headerRowsToScan.add(rowIter.next()) // first 3 rows
        headerRowsToScan.add(rowIter.next())
        headerRowsToScan.add(rowIter.next())
        headerRowsToScan.add(rowIterInd.next()) // first 2 rows
        headerRowsToScan.add(rowIterInd.next())


        // find indices for desired columns
        columnLocations.clear()
        for(row in headerRowsToScan) {
            for(cellIndex in 0 until 30) {
                val cellString = row.getCell(cellIndex)?.stringCellValue ?: continue
                COLUMN_NAMES.filter { cellString.contains(it)}.forEach {
                    val list = columnLocations.computeIfAbsent(it) { LinkedList() }
                    list.add(DataColumnLocation(row.sheet, cellIndex))
                }
            }
        }


        while(rowIter.hasNext()) {
            val row = mapOf(
                    sheetSum to rowIter.next(),
                    sheetIndications to rowIterInd.next()
            )

//            if(row.getCell(columnLocations[COLUMN_TOTAL_VACC]?.first()?.index ?: 2).cellType != CellType.NUMERIC || row.getCell(columnLocations[COLUMN_STATE]?.first()?.index ?: 1).stringCellValue.equals("Gesamt")) {
//                break
//            }

            if(row[sheetSum]!!.getCell(0).cellType != CellType.NUMERIC && row[sheetSum]!!.getCell(0).stringCellValue.isBlank()) {
                break
            }


            val stateInfo = StateVaccinationInfo(
                row.getCell(COLUMN_STATE, 0)?.stringCellValue ?: "unknown",
                date,
                row.getCell(COLUMN_TOTAL_VACC, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_CHANGE_VACC, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_AGED, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_JOB, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_MEDICAL, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_NURSING_HOME, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_TOTAL_VACC, 1)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_CHANGE_VACC, 1)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_AGED, 1)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_JOB, 1)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_MEDICAL, 1)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_NURSING_HOME, 1)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_BIONTECH, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_MODERNA, 0)?.numericCellValue?.toInt() ?: 0,
                row.getCell(COLUMN_COUNT_ASTRAZENECA, 0)?.numericCellValue?.toInt() ?: 0
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
                result.values.sumBy { it.countNursingHome },
                result.values.sumBy { it.count_2 },
                result.values.sumBy { it.countChange_2 },
                result.values.sumBy { it.countAged_2 },
                result.values.sumBy { it.countJob_2 },
                result.values.sumBy { it.countMedical_2 },
                result.values.sumBy { it.countNursingHome_2 },
                result.values.sumBy { it.countBioNTech },
                result.values.sumBy { it.countModerna },
                result.values.sumBy { it.countAstraZeneca }
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
        val countNursingHome: Int,
        val count_2: Int,
        val countChange_2: Int,
        val countAged_2: Int,
        val countJob_2: Int,
        val countMedical_2: Int,
        val countNursingHome_2: Int,
        val countBioNTech: Int,
        val countModerna: Int,
        val countAstraZeneca: Int
    ) {
        fun toInfoString() : String {
            return String.format("${toDayString(date)}: %.2f%% (+%.2f%%), %d (+%d)", count.toDouble() / population.toDouble() * 100.0, countChange.toDouble() / population.toDouble() * 100.0, count, countChange)
        }
        fun toInfoStringSecond() : String {
            return String.format("${toDayString(date)}: %.2f%% (+%.2f%%), %d (+%d)", count_2.toDouble() / population.toDouble() * 100.0, countChange_2.toDouble() / population.toDouble() * 100.0, count_2, countChange_2)
        }

        val population = populations[stateName]!!
        val dayString = LGLParser.toDayString(date)
    }

    data class DataColumnLocation(val sheet: Sheet, val index: Int)
    fun Map<Sheet, Row>.getCell(columnName: String, occurence: Int): Cell? {
        val location = (columnLocations[columnName] ?: return null)[occurence]
        val row = this[location.sheet] ?: return null
        return row.getCell(location.index)
    }

    fun isDataSheet(it: Sheet): Boolean = it.getRow(0).getCell(0).stringCellValue.equals("Bundesland") ||
                (it.getRow(0).getCell(1) != null && it.getRow(0).getCell(1).stringCellValue.equals("Bundesland"))
}