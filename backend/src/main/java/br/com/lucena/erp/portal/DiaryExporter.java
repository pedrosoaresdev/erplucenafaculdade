package br.com.lucena.erp.portal;

import br.com.lucena.erp.portal.PortalDtos.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exportação do diário (notas, médias, frequência e situação) em XLSX e CSV. */
@Component
public class DiaryExporter {
    static final Map<String, String> SITUATIONS = Map.of("EM_CURSO", "Em curso", "APROVADO", "Aprovado",
            "REPROVADO_NOTA", "Reprovado por nota", "REPROVADO_FREQUENCIA", "Reprovado por frequência");

    public byte[] xlsx(DiaryView d) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Notas e frequência");
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            CellStyle num = wb.createCellStyle();
            num.setDataFormat(wb.createDataFormat().getFormat("0.00"));

            TeachingView t = d.teaching();
            row(sh, 0, bold, t.subjectName() + " — turma " + t.classCode() + " (" + t.termCode() + ")");
            row(sh, 1, null, t.courseName() + " · nota mínima " + d.minGrade().toPlainString() + " · frequência mínima " + d.minAttendancePct().toPlainString() + "%");
            List<String> header = header(d);
            Row h = sh.createRow(3);
            for (int i = 0; i < header.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(header.get(i));
                c.setCellStyle(bold);
            }
            int r = 4;
            for (StudentRow s : d.students()) {
                Row row = sh.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(s.registration());
                row.createCell(c++).setCellValue(s.name());
                for (AssessmentView a : d.assessments()) c = decimal(row, c, s.scores().get(a.id()), num);
                GradeCalculator.Result res = s.result();
                c = decimal(row, c, res.average(), num);
                c = decimal(row, c, res.recovery(), num);
                c = decimal(row, c, res.finalGrade(), num);
                row.createCell(c++).setCellValue(res.totalLessons());
                row.createCell(c++).setCellValue(res.absences());
                c = decimal(row, c, res.attendancePct(), num);
                row.createCell(c).setCellValue(SITUATIONS.get(res.situation().name()));
            }
            sh.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(3, header.size() - 1)));
            for (int i = 0; i < header.size(); i++) sh.autoSizeColumn(i);
            sh.createFreezePane(2, 4);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX", e);
        }
    }

    /** CSV com separador ';' e BOM UTF-8 (abre corretamente no Excel em português). */
    public byte[] csv(DiaryView d) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(";", header(d).stream().map(DiaryExporter::esc).toList())).append('\n');
        for (StudentRow s : d.students()) {
            List<String> cols = new ArrayList<>(List.of(esc(s.registration()), esc(s.name())));
            for (AssessmentView a : d.assessments()) cols.add(dec(s.scores().get(a.id())));
            GradeCalculator.Result res = s.result();
            cols.addAll(List.of(dec(res.average()), dec(res.recovery()), dec(res.finalGrade()), String.valueOf(res.totalLessons()),
                    String.valueOf(res.absences()), dec(res.attendancePct()), esc(SITUATIONS.get(res.situation().name()))));
            sb.append(String.join(";", cols)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> header(DiaryView d) {
        List<String> h = new ArrayList<>(List.of("Matrícula", "Aluno"));
        d.assessments().forEach(a -> h.add(a.title() + " (máx. " + a.maxScore().stripTrailingZeros().toPlainString() + ", peso " + a.weight().stripTrailingZeros().toPlainString() + ")"));
        h.addAll(List.of("Média", "Recuperação", "Nota final", "Aulas", "Faltas", "Frequência %", "Situação"));
        return h;
    }

    private static void row(Sheet sh, int idx, CellStyle style, String text) {
        Cell c = sh.createRow(idx).createCell(0);
        c.setCellValue(text);
        if (style != null) c.setCellStyle(style);
    }

    private static int decimal(Row row, int c, BigDecimal v, CellStyle style) {
        Cell cell = row.createCell(c);
        if (v != null) {
            cell.setCellValue(v.doubleValue());
            cell.setCellStyle(style);
        }
        return c + 1;
    }

    private static String dec(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Escapa aspas e neutraliza fórmulas (CSV injection). */
    private static String esc(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
