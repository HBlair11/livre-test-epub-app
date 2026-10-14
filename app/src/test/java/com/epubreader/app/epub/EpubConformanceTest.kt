package com.epubreader.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubConformanceTest {

    @Test
    fun `conformance test - EPUB with malformed metadata and missing TOC defaults gracefully`() {
        val file = createTestEpub(
            opf = """
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="item1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="item1"/>
                  </spine>
                </package>
            """.trimIndent(),
            files = mapOf(
                "ch1.xhtml" to "<html><body><h1>Malformed Book</h1><p>Content without explicit TOC.</p></body></html>"
            )
        )

        val book = EpubParser().parse(file)
        assertNotNull(book)
        assertEquals(1, book.spine.size)
        assertTrue(book.spine.first().href.contains("ch1.xhtml"))
    }

    @Test
    fun `conformance test - EPUB 3 with complex assets SVG CJK RTL anchors and URL encoded paths`() {
        val file = createTestEpub(
            opf = """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Complex Corpus Book</dc:title>
                    <dc:creator>著者 Test Author</dc:creator>
                    <dc:identifier id="uid">urn:uuid:12345</dc:identifier>
                    <dc:language>ja</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="chapter%20one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img1" href="images/cover%20art.webp" media-type="image/webp"/>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                  </manifest>
                  <spine>
                    <itemref idref="ch1"/>
                  </spine>
                </package>
            """.trimIndent(),
            files = mapOf(
                "chapter%20one.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml" dir="rtl" xml:lang="ja">
                      <body>
                        <h1 id="intro">第1章</h1>
                        <p>CJK text with tables and footnotes <a href="#fn1">1</a>.</p>
                        <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><circle cx="50" cy="50" r="40"/></svg>
                        <p><a href="https://external.link/resource">External Link</a></p>
                      </body>
                    </html>
                """.trimIndent(),
                "nav.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
                      <nav epub:type="toc"><ol><li><a href="chapter%20one.xhtml#intro">Chapter 1 Intro</a></li></ol></nav>
                    </body></html>
                """.trimIndent(),
                "images/cover%20art.webp" to "WEBP_DUMMY_BYTES"
            )
        )

        val book = EpubParser().parse(file)
        assertEquals("Complex Corpus Book", book.metadata.title)
        assertEquals("著者 Test Author", book.metadata.authorString)
        assertEquals(1, book.spine.size)
        assertTrue(book.spine.first().href.contains("chapter"))
        assertEquals(1, book.toc.size)
        assertTrue(book.toc.first().href.contains("#intro"))
    }

    private fun createTestEpub(opf: String, files: Map<String, String>): File {
        val file = File.createTempFile("livre_conformance_test_", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml" /></rootfiles>
                </container>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()

            files.forEach { (name, content) ->
                val decodedName = URLDecoder.decode(name, "UTF-8")
                zip.putNextEntry(ZipEntry("OEBPS/$decodedName"))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        file.deleteOnExit()
        assertTrue(file.exists())
        return file
    }
}
