/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */
package org.nuxeo.ecm.core.convert.plugins.text.extractors;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.core.convert.extension.Converter;

public class XLX2TextConverter extends BaseOfficeXMLTextConverter implements Converter {

    private static final Logger log = LogManager.getLogger(XLX2TextConverter.class);

    protected static final String CELL_SEP = " ";

    protected static final String ROW_SEP = "\n";

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) {

        StringBuilder sb = new StringBuilder();

        try {
            Blob blob = blobHolder.getBlob();

            if (blob.getLength() < 0 || blob.getLength() > maxSize4POI) {
                return runFallBackConverter(blobHolder, "xl/");
            }

            try (InputStream stream = blob.getStream(); //
                    OPCPackage p = OPCPackage.open(stream); //
                    XSSFWorkbook workbook = new XSSFWorkbook(p)) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    XSSFSheet sheet = workbook.getSheetAt(i);
                    Iterator<Row> rows = sheet.rowIterator();
                    while (rows.hasNext()) {
                        XSSFRow row = (XSSFRow) rows.next();
                        Iterator<Cell> cells = row.cellIterator();
                        while (cells.hasNext()) {
                            XSSFCell cell = (XSSFCell) cells.next();
                            appendTextFromCell(cell, sb);
                        }
                        sb.append(ROW_SEP);
                    }
                }
            }
            return new SimpleCachableBlobHolder(Blobs.createBlob(sb.toString()));
        } catch (IOException | OpenXML4JException e) {
            throw new ConversionException("Error during XLX2Text conversion", blobHolder, e);
        }
    }

    protected void appendTextFromCell(XSSFCell cell, StringBuilder sb) {
        String cellValue;
        switch (cell.getCellType()) {
            case NUMERIC -> cellValue = Double.toString(cell.getNumericCellValue());
            case STRING -> cellValue = cell.getStringCellValue().trim().replace("\n", " ");
            default -> {
                // Ignore formulas and other unknown cell types
                cellValue = null;
                log.debug("Skipping text from unsupported CellType: {}", cell.getCellType());
            }
        }

        if (cellValue != null && !cellValue.isBlank()) {
            sb.append(cellValue).append(CELL_SEP);
        }
    }

}
