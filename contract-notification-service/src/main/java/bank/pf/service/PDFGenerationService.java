package bank.pf.service;

import bank.pf.dto.LoanTerms;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
public class PDFGenerationService {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(LOCALE_BR);
    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance(LOCALE_BR);

    public byte[] generateLoanContractPdf(String applicationId, String cpf, LoanTerms terms) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(byteArrayOutputStream);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            PERCENT_FORMAT.setMinimumFractionDigits(2);
            PERCENT_FORMAT.setMaximumFractionDigits(4);


            // Cabeçalho
            document.add(new Paragraph("CONTRATO DE EMPRÉSTIMO PESSOAL")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBold()
                    .setFontSize(18)
                    .setMarginBottom(20));

            document.add(new Paragraph("Data de Emissão: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_BR))));
            document.add(new Paragraph("ID da Solicitação: " + applicationId));
            document.add(new Paragraph("CPF do Contratante: " + cpf).setMarginBottom(15));

            // Partes (Simplificado)
            document.add(new Paragraph("PARTES CONTRATANTES:").setBold().setMarginBottom(5));
            document.add(new Paragraph("CONTRATADA: CredFácil Soluções Financeiras Ltda., CNPJ 00.000.000/0001-00, com sede em Rua Fictícia, 123, Cidade Exemplo.").setMarginBottom(5));
            document.add(new Paragraph("CONTRATANTE: Cliente qualificado pelo CPF " + cpf + ".").setMarginBottom(15));

            // Termos do Empréstimo
            document.add(new Paragraph("TERMOS DO EMPRÉSTIMO:").setBold().setMarginBottom(5));
            document.add(new Paragraph("1. VALOR DO EMPRÉSTIMO (PRINCIPAL): " + CURRENCY_FORMAT.format(terms.getApprovedAmount())));
            document.add(new Paragraph("2. TAXA DE JUROS ANUAL (CET aproximada): " + PERCENT_FORMAT.format(terms.getInterestRate())));
            document.add(new Paragraph("3. NÚMERO DE PARCELAS: " + terms.getNumberOfInstallments()));
            document.add(new Paragraph("4. VALOR DA PARCELA MENSAL: " + CURRENCY_FORMAT.format(terms.getInstallmentAmount())));
            // Adicionar Custo Efetivo Total (CET) se calculado e disponível.
            // Adicionar data de vencimento da primeira e última parcela, etc.

            document.add(new Paragraph("5. FORMA DE PAGAMENTO: Débito em conta corrente ou boleto bancário (a ser definido).").setMarginBottom(15));

            // Cláusulas Simplificadas
            document.add(new Paragraph("CLÁUSULAS GERAIS:").setBold().setMarginBottom(5));
            document.add(new Paragraph("O CONTRATANTE declara estar ciente e de acordo com todos os termos e condições aqui estabelecidos. O atraso no pagamento das parcelas implicará em multa e juros moratórios, conforme legislação vigente e termos detalhados no Anexo I (não incluso nesta simulação).").setMarginBottom(15));


            // Assinaturas (Placeholder)
            document.add(new Paragraph("_________________________________________").setTextAlignment(TextAlignment.CENTER).setMarginTop(50));
            document.add(new Paragraph("Assinatura do Contratante (CPF: " + cpf + ")").setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("_________________________________________").setTextAlignment(TextAlignment.CENTER).setMarginTop(30));
            document.add(new Paragraph("CredFácil Soluções Financeiras Ltda.").setTextAlignment(TextAlignment.CENTER));

            document.close();
            log.info("Contrato PDF gerado com sucesso para a aplicação {}", applicationId);
            return byteArrayOutputStream.toByteArray();

        } catch (Exception e) {
            log.error("Erro ao gerar contrato PDF para aplicação {}: {}", applicationId, e.getMessage(), e);
            // Lançar uma exceção customizada ou retornar null/empty array, dependendo da política de erro
            throw new RuntimeException("Falha ao gerar PDF do contrato", e);
        }
    }
}
