package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.FinancialTransaction
import com.example.data.model.TransactionType
import com.example.ui.viewmodel.MonthlyChartPoint
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FinancialExportRequest(
  val businessName: String,
  val periodTitle: String,
  val fileSlug: String,
  val transactions: List<FinancialTransaction>,
  val monthlyBreakdown: List<MonthlyChartPoint> = emptyList(),
)

object FinancialReportExporter {

  private const val A4_WIDTH = 595
  private const val A4_HEIGHT = 842
  private const val MARGIN_X = 36f
  private const val CONTENT_RIGHT = A4_WIDTH - 36f
  private const val PAGE_BOTTOM_LIMIT = A4_HEIGHT - 48f

  fun suggestedPdfFileName(slug: String): String {
    val cleanSlug = sanitizeFileSlug(slug)
    return "malvinas_balance_$cleanSlug.pdf"
  }

  fun suggestedCsvFileName(slug: String): String {
    val cleanSlug = sanitizeFileSlug(slug)
    return "malvinas_balance_$cleanSlug.csv"
  }

  /**
   * Neutralizes spreadsheet formula injection (CWE-1236) and applies RFC 4180 CSV escaping.
   */
  internal fun escapeCsvCell(raw: String): String {
    val trimmed = raw.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    val safe =
      if (
        trimmed.startsWith("=") ||
          trimmed.startsWith("+") ||
          trimmed.startsWith("-") ||
          trimmed.startsWith("@") ||
          trimmed.startsWith("\t")
      ) {
        "'$trimmed"
      } else {
        trimmed
      }
    val escaped = safe.replace("\"", "\"\"")
    return "\"$escaped\""
  }

  fun buildCsvReport(request: FinancialExportRequest): String {
    val totalIncome =
      request.transactions
        .filter { it.type == TransactionType.INCOME.id }
        .sumOf { it.amount }
    val totalExpenses =
      request.transactions
        .filter { it.type == TransactionType.EXPENSE.id }
        .sumOf { it.amount }
    val netBalance = totalIncome - totalExpenses

    val sb = StringBuilder()
    // UTF-8 BOM so Microsoft Excel on Windows/Mac displays Spanish accents properly
    sb.append('\uFEFF')
    sb.appendLine("${escapeCsvCell(request.businessName.ifBlank { "MALVINAS PRO SERVICE" })};${escapeCsvCell("Balance: ${request.periodTitle}")}")
    sb.appendLine("${escapeCsvCell("Ingresos totales")};${String.format(Locale.US, "%.2f", totalIncome)}")
    sb.appendLine("${escapeCsvCell("Gastos totales")};${String.format(Locale.US, "%.2f", totalExpenses)}")
    sb.appendLine("${escapeCsvCell("Balance neto")};${String.format(Locale.US, "%.2f", netBalance)}")
    sb.appendLine()

    if (request.monthlyBreakdown.isNotEmpty()) {
      sb.appendLine("Mes;Ingresos;Gastos;Balance Neto")
      request.monthlyBreakdown.forEach { point ->
        sb.appendLine(
          listOf(
            escapeCsvCell(point.fullLabel),
            String.format(Locale.US, "%.2f", point.totalIncome),
            String.format(Locale.US, "%.2f", point.totalExpenses),
            String.format(Locale.US, "%.2f", point.netBalance),
          ).joinToString(";")
        )
      }
      sb.appendLine()
    }

    sb.appendLine("Fecha;Mes;Tipo;Concepto;Categoría;Monto;Notas")
    request.transactions
      .sortedByDescending { it.dateMillis }
      .forEach { tx ->
        val typeLabel = if (tx.type == TransactionType.INCOME.id) "Ingreso" else "Gasto"
        val signedAmount =
          if (tx.type == TransactionType.INCOME.id) tx.amount else -tx.amount
        sb.appendLine(
          listOf(
            escapeCsvCell(Formatters.formatDate(tx.dateMillis)),
            escapeCsvCell(tx.monthKey),
            escapeCsvCell(typeLabel),
            escapeCsvCell(tx.title),
            escapeCsvCell(tx.category.ifBlank { "General" }),
            String.format(Locale.US, "%.2f", signedAmount),
            escapeCsvCell(tx.notes),
          ).joinToString(";")
        )
      }

    return sb.toString()
  }

  fun buildPdfBytes(request: FinancialExportRequest): ByteArray {
    val pdf = PdfDocument()
    val totalIncome =
      request.transactions
        .filter { it.type == TransactionType.INCOME.id }
        .sumOf { it.amount }
    val totalExpenses =
      request.transactions
        .filter { it.type == TransactionType.EXPENSE.id }
        .sumOf { it.amount }
    val netBalance = totalIncome - totalExpenses
    val marginPct = if (totalIncome > 0.0) (((netBalance) / totalIncome) * 100.0).toInt() else 0

    val brandBlue = Color.rgb(16, 118, 214)
    val accentGold = Color.rgb(214, 158, 24)
    val incomeGreen = Color.rgb(19, 138, 86)
    val expenseRed = Color.rgb(198, 40, 40)
    val darkText = Color.rgb(24, 32, 42)
    val mutedText = Color.rgb(94, 108, 124)
    val softSurface = Color.rgb(243, 247, 252)
    val dividerGray = Color.rgb(218, 226, 236)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = dividerGray
      strokeWidth = 1f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 18f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(230, 242, 255)
      textSize = 11f
    }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = darkText
      textSize = 13f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = darkText
      textSize = 10f
    }
    val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = darkText
      textSize = 10f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = mutedText
      textSize = 9.2f
    }
    val rightAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = 10f
      textAlign = Paint.Align.RIGHT
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var pageNumber = 1
    var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create())
    var canvas = currentPage.canvas
    var cursorY = 0f

    fun drawPageFooter() {
      val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR")).format(Date())
      canvas.drawLine(MARGIN_X, A4_HEIGHT - 34f, CONTENT_RIGHT, A4_HEIGHT - 34f, linePaint)
      canvas.drawText(
        "Generado el $stamp · ${request.businessName.ifBlank { "Malvinas Pro Service" }}",
        MARGIN_X,
        A4_HEIGHT - 18f,
        mutedPaint,
      )
      val pageLabelPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
      canvas.drawText("Página $pageNumber", CONTENT_RIGHT, A4_HEIGHT - 18f, pageLabelPaint)
    }

    fun startContinuationPage() {
      drawPageFooter()
      pdf.finishPage(currentPage)
      pageNumber++
      currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create())
      canvas = currentPage.canvas
      fillPaint.color = brandBlue
      canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 32f, fillPaint)
      canvas.drawText(
        "${request.businessName.ifBlank { "MALVINAS PRO SERVICE" }} · ${request.periodTitle}",
        MARGIN_X,
        21f,
        subtitlePaint,
      )
      cursorY = 52f
    }

    fun ensureSpace(requiredHeight: Float) {
      if (cursorY + requiredHeight > PAGE_BOTTOM_LIMIT) {
        startContinuationPage()
      }
    }

    // Header banner on page 1
    fillPaint.color = brandBlue
    canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 84f, fillPaint)
    fillPaint.color = accentGold
    canvas.drawRect(0f, 84f, A4_WIDTH.toFloat(), 89f, fillPaint)

    canvas.drawText(
      request.businessName.ifBlank { "MALVINAS PRO SERVICE" },
      MARGIN_X,
      36f,
      titlePaint,
    )
    canvas.drawText(
      "Reporte financiero · ${request.periodTitle}",
      MARGIN_X,
      58f,
      subtitlePaint,
    )
    canvas.drawText(
      "${request.transactions.size} movimiento(s) registrados",
      MARGIN_X,
      73f,
      subtitlePaint,
    )

    cursorY = 110f

    // 3 KPI Summary Cards
    val boxGap = 10f
    val boxWidth = (CONTENT_RIGHT - MARGIN_X - boxGap * 2) / 3f
    val boxHeight = 58f
    val kpis =
      listOf(
        Triple("INGRESOS", Formatters.formatCurrency(totalIncome), incomeGreen),
        Triple("GASTOS", Formatters.formatCurrency(totalExpenses), expenseRed),
        Triple(
          "BALANCE NETO ($marginPct%)",
          Formatters.formatCurrency(netBalance),
          if (netBalance >= 0) brandBlue else expenseRed,
        ),
      )

    kpis.forEachIndexed { idx, (label, value, accent) ->
      val left = MARGIN_X + idx * (boxWidth + boxGap)
      fillPaint.color = softSurface
      canvas.drawRoundRect(left, cursorY, left + boxWidth, cursorY + boxHeight, 8f, 8f, fillPaint)
      fillPaint.color = accent
      canvas.drawRect(left, cursorY, left + 4f, cursorY + boxHeight, fillPaint)
      canvas.drawText(label, left + 12f, cursorY + 20f, mutedPaint)
      val valPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accent
          textSize = 14f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      canvas.drawText(ellipsize(value, 18), left + 12f, cursorY + 43f, valPaint)
    }

    cursorY += boxHeight + 22f

    // Monthly Summary Table (when multi-month)
    if (request.monthlyBreakdown.size > 1) {
      ensureSpace(44f)
      canvas.drawText("Evolución mensual", MARGIN_X, cursorY, sectionPaint)
      cursorY += 14f

      fillPaint.color = softSurface
      canvas.drawRect(MARGIN_X, cursorY, CONTENT_RIGHT, cursorY + 20f, fillPaint)
      canvas.drawText("Mes", MARGIN_X + 6f, cursorY + 14f, bodyBoldPaint)
      val colIncomeX = MARGIN_X + 250f
      val colExpenseX = MARGIN_X + 380f
      val colNetX = CONTENT_RIGHT - 6f
      val headerRight = Paint(bodyBoldPaint).apply { textAlign = Paint.Align.RIGHT }
      canvas.drawText("Ingresos", colIncomeX, cursorY + 14f, headerRight)
      canvas.drawText("Gastos", colExpenseX, cursorY + 14f, headerRight)
      canvas.drawText("Neto", colNetX, cursorY + 14f, headerRight)
      cursorY += 24f

      request.monthlyBreakdown.forEach { point ->
        ensureSpace(20f)
        canvas.drawText(ellipsize(point.fullLabel, 24), MARGIN_X + 6f, cursorY + 11f, bodyPaint)
        rightAmountPaint.color = incomeGreen
        canvas.drawText(Formatters.formatCurrency(point.totalIncome), colIncomeX, cursorY + 11f, rightAmountPaint)
        rightAmountPaint.color = expenseRed
        canvas.drawText(Formatters.formatCurrency(point.totalExpenses), colExpenseX, cursorY + 11f, rightAmountPaint)
        rightAmountPaint.color = if (point.netBalance >= 0) brandBlue else expenseRed
        canvas.drawText(Formatters.formatCurrency(point.netBalance), colNetX, cursorY + 11f, rightAmountPaint)
        cursorY += 18f
        canvas.drawLine(MARGIN_X, cursorY, CONTENT_RIGHT, cursorY, linePaint)
      }
      cursorY += 18f
    }

    // Transactions table
    ensureSpace(44f)
    canvas.drawText("Detalle de movimientos", MARGIN_X, cursorY, sectionPaint)
    cursorY += 14f

    fun drawTxTableHeader() {
      fillPaint.color = softSurface
      canvas.drawRect(MARGIN_X, cursorY, CONTENT_RIGHT, cursorY + 20f, fillPaint)
      canvas.drawText("Fecha", MARGIN_X + 6f, cursorY + 14f, bodyBoldPaint)
      canvas.drawText("Concepto", MARGIN_X + 74f, cursorY + 14f, bodyBoldPaint)
      canvas.drawText("Categoría", MARGIN_X + 305f, cursorY + 14f, bodyBoldPaint)
      val headerRight = Paint(bodyBoldPaint).apply { textAlign = Paint.Align.RIGHT }
      canvas.drawText("Monto", CONTENT_RIGHT - 6f, cursorY + 14f, headerRight)
      cursorY += 24f
    }

    drawTxTableHeader()

    if (request.transactions.isEmpty()) {
      canvas.drawText("Sin movimientos registrados en este período.", MARGIN_X + 6f, cursorY + 12f, mutedPaint)
    } else {
      request.transactions
        .sortedByDescending { it.dateMillis }
        .forEach { tx ->
          if (cursorY + 20f > PAGE_BOTTOM_LIMIT) {
            startContinuationPage()
            drawTxTableHeader()
          }
          val isIncome = tx.type == TransactionType.INCOME.id
          canvas.drawText(Formatters.formatDate(tx.dateMillis), MARGIN_X + 6f, cursorY + 12f, mutedPaint)
          canvas.drawText(ellipsize(tx.title, 38), MARGIN_X + 74f, cursorY + 12f, bodyPaint)
          canvas.drawText(ellipsize(tx.category.ifBlank { "General" }, 18), MARGIN_X + 305f, cursorY + 12f, mutedPaint)
          rightAmountPaint.color = if (isIncome) incomeGreen else expenseRed
          val prefix = if (isIncome) "+" else "-"
          canvas.drawText(
            "$prefix${Formatters.formatCurrency(tx.amount)}",
            CONTENT_RIGHT - 6f,
            cursorY + 12f,
            rightAmountPaint,
          )
          cursorY += 19f
          canvas.drawLine(MARGIN_X, cursorY, CONTENT_RIGHT, cursorY, linePaint)
        }
    }

    drawPageFooter()
    pdf.finishPage(currentPage)

    return ByteArrayOutputStream().use { baos ->
      pdf.writeTo(baos)
      pdf.close()
      baos.toByteArray()
    }
  }

  /**
   * Writes report to strictly confined cacheDir/exports/ directory and opens Android Share Sheet
   * using FileProvider (zero storage permissions needed).
   */
  fun shareReportBytes(
    context: Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    chooserTitle: String,
  ): Boolean {
    return runCatching {
      val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(80)
      val outFile = File(exportsDir, safeName)
      // Path confinement verification
      val canonicalBase = exportsDir.canonicalPath + File.separator
      require(outFile.canonicalPath.startsWith(canonicalBase)) { "Ruta de archivo inválida." }

      outFile.writeBytes(bytes)
      val uri =
        FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          outFile,
        )
      val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = mimeType
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(
        Intent.createChooser(shareIntent, chooserTitle).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      )
      true
    }.getOrDefault(false)
  }

  private fun sanitizeFileSlug(raw: String): String =
    raw.lowercase(Locale.ROOT)
      .replace(Regex("[^a-z0-9_\\-]"), "_")
      .replace(Regex("_+"), "_")
      .trim('_')
      .ifBlank { "reporte" }
      .take(40)

  private fun ellipsize(text: String, maxChars: Int): String =
    if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"
}
