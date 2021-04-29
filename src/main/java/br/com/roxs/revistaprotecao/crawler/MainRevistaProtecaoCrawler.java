package br.com.roxs.revistaprotecao.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.protocol.HttpClientContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;

public class MainRevistaProtecaoCrawler {

	private static final Logger logger = LoggerFactory.getLogger(MainRevistaProtecaoCrawler.class);

	private static final String BASE_HOST = "https://bc.pressmatrix.com";

	private static final String BASE_RES = BASE_HOST + "/pt-BR/profiles/1227998e328d";

	private static final String LOGIN_URL = BASE_RES + "/users/sign_in";

	private static final String EDITIONS_URL = BASE_RES + "/editions";

	public static void main(String[] args) throws Exception {

		try {

			if (args.length < 2) {
				throw new IllegalArgumentException("Forneça as credenciais de acesso");
			}

			String username = args[0];
			String password = args[1];

			HttpClientContext context = Util.createHttpClientContext();

			String page = Util.get(LOGIN_URL, context);

			Map<String, String> params = Util.getFormInputs(page);
			params.put("user[email]", username);
			params.put("user[password]", password);

			page = Util.post(LOGIN_URL, params, context);

			NumberFormat nf = new DecimalFormat("000");

			if (page.contains("redirected")) {
				page = Util.get(EDITIONS_URL, context);

				Elements editions = Jsoup.parse(page).select(".module-teaser a");

				next_edition: for (Element e : editions) {

					String url = e.attr("href");

					String editionId = url.substring(url.lastIndexOf('/') + 1);

					String label = e.select(".cover-image").attr("alt");

					String data = e.select("figcaption span").get(0).text().trim();

					String editionFileName = label.replaceAll("/", "-") + " - " + data.replaceAll("/", "-");

					File editionDir = new File("editions", editionFileName);

					File pdfFile = new File(editionDir, editionFileName + ".pdf");

					if (pdfFile.exists()) {
						continue;
					}

					logger.info("Processando edição " + label + " (" + data + ")");

					page = Util.get(BASE_RES + "/editions/" + editionId + "/pages", context);

					Elements pags = Jsoup.parse(page).select(".module-pagination .item");

					int totalPages = Integer.valueOf(pags.get(pags.size() - 1).text().trim());

					File pagesDir = new File(editionDir, "pages");
					pagesDir.mkdirs();

					int p = 1;

					List<File> pages = new ArrayList<File>(totalPages);

					for (int i = 1; i <= totalPages; i++) {

						Elements pageImgs = Jsoup.parse(Util.get(BASE_RES + "/editions/" + editionId + "/pages/page/" + i, context)).select(".page img");

						for (Element img : pageImgs) {

							String pageUrl = img.attr("src");

							File f = new File(pagesDir, nf.format(p) + ".jpg");

							pages.add(f);

							if (!f.exists()) {

								try (OutputStream out = new FileOutputStream(f)) {
									Util.download(pageUrl, context, out);

									logger.info("Download efetuado da página " + p + " da edição " + label + " --> " + f.length() + " bytes");

								} catch (Exception ex) {
									f.delete();
									logger.error("Erro efetuando download da página " + p + " da edição " + label + " --> " + f.length() + " bytes");
									continue next_edition;
								}

							}

							p++;
						}

					}

					buildPdf(pages, pdfFile);
					logger.info("Arquivo PDF da edição " + label + " criado --> " + pdfFile.length() + " bytes");
				}
			}

		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
	}

	private static void buildPdf(List<File> images, File pdfFile) throws Exception {

		try (OutputStream os = new FileOutputStream(pdfFile)) {

			Document document = new Document();

			try {
				PdfWriter.getInstance(document, os);

				document.open();

				for (int i = 0; i < images.size(); i++) {
					Image image = Image.getInstance(images.get(i).getAbsolutePath());
					document.setPageSize(new Rectangle(image.getWidth(), image.getHeight()));
					document.setMargins(0, 0, 0, 0);
					document.newPage();
					document.add(image);
				}
			} finally {
				document.close();
			}
		}
	}

}
