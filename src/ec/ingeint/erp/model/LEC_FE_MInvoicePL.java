package ec.ingeint.erp.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.compiere.model.MBPartner;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoicePaySchedule;
import org.compiere.model.MLocation;
import org.compiere.model.MNote;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MSysConfig;
import org.compiere.model.Query;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.globalqss.model.LEC_FE_MRetencion;
import org.globalqss.model.LEC_FE_ModelValidator;
import org.globalqss.model.X_LCO_TaxIdType;
import org.globalqss.model.X_LEC_SRI_Format;
import org.globalqss.model.X_SRI_AccessCode;
import org.globalqss.model.X_SRI_Authorization;
import org.globalqss.util.LEC_FE_Utils;
import org.globalqss.util.LEC_FE_UtilsXml;
import org.xml.sax.helpers.AttributesImpl;

public class LEC_FE_MInvoicePL extends MInvoice {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4691462020138332667L;

	private int m_lec_sri_format_id = 0;

	private String file_name = "";
	private String m_obligadocontabilidad = "NO";
	private String m_coddoc = "";
	private String m_accesscode;
	private String m_tipoidentificacioncomprador = "";
	private String m_identificacioncomprador = "";
	private String m_razonsocial = "";
	private boolean IsGenerateInBatch = false;

	private BigDecimal m_totaldescuento = Env.ZERO;
	private BigDecimal m_totalbaseimponible = Env.ZERO;
	private BigDecimal m_totalvalorimpuesto = Env.ZERO;
	private BigDecimal m_sumadescuento = Env.ZERO;
	private BigDecimal m_sumabaseimponible = Env.ZERO;
	private BigDecimal m_sumavalorimpuesto = Env.ZERO;
	private BigDecimal m_withholdingamt = Env.ZERO;

	// 04/07/2016 MHG Offline Schema added
	private boolean isOfflineSchema = false;

	public LEC_FE_MInvoicePL(Properties ctx, int C_Invoice_ID, String trxName) {
		super(ctx, C_Invoice_ID, trxName);

		// 04/07/2016 MHG Offline Schema added
		isOfflineSchema = MSysConfig.getBooleanValue("QSSLEC_FE_OfflineSchema", false,
				Env.getAD_Client_ID(Env.getCtx()));

	}

	public String lecfeinv_SriExportInvoicePLXML100() {

		String msg = null;
		X_SRI_Authorization a = null;
		LEC_FE_UtilsXml signature = new LEC_FE_UtilsXml();

		try {

			signature.setPKCS12_Resource(
					MSysConfig.getValue("QSSLEC_FE_RutaCertificadoDigital", null, getAD_Client_ID(), getAD_Org_ID()));
			signature.setPKCS12_Password(
					MSysConfig.getValue("QSSLEC_FE_ClaveCertificadoDigital", null, getAD_Client_ID(), getAD_Org_ID()));
			signature.setAD_Org_ID(getAD_Org_ID());

			if (signature.getFolderRaiz() == null)
				return "Error en Liquidación No " + getDocumentNo() + " No existe parametro para Ruta Generacion Xml";

			MDocType dt = new MDocType(getCtx(), getC_DocTypeTarget_ID(), get_TrxName());

			m_coddoc = dt.get_ValueAsString("SRI_ShortDocType");

			if (m_coddoc.equals(""))
				return "Error en Factura No " + getDocumentNo() + " No existe definicion SRI_ShortDocType: "
						+ dt.toString();

			// Formato
			m_lec_sri_format_id = LEC_FE_Utils.getLecSriFormat(getAD_Client_ID(), signature.getDeliveredType(),
					m_coddoc, getDateInvoiced(), getDateInvoiced());

			if (m_lec_sri_format_id < 1)
				return "Error en Factura No " + getDocumentNo() + " No existe formato para el comprobante";

			X_LEC_SRI_Format f = new X_LEC_SRI_Format(getCtx(), m_lec_sri_format_id, get_TrxName());

			// Emisor
			MOrgInfo oi = MOrgInfo.get(getCtx(), getAD_Org_ID(), get_TrxName());

			msg = LEC_FE_ModelValidator.valideOrgInfoSri(oi);

			if (msg != null)
				return "Error en Liquidación No " + getDocumentNo() + " " + msg;

			if ((Boolean) oi.get_Value("SRI_IsKeepAccounting"))
				m_obligadocontabilidad = "SI";

			int c_bpartner_id = LEC_FE_Utils.getOrgBPartner(getAD_Client_ID(), oi.get_ValueAsString("TaxID"));
			MBPartner bpe = new MBPartner(getCtx(), c_bpartner_id, get_TrxName());

			MLocation lo = new MLocation(getCtx(), oi.getC_Location_ID(), get_TrxName());

			int c_location_matriz_id = MSysConfig.getIntValue("QSSLEC_FE_LocalizacionDireccionMatriz", -1,
					oi.getAD_Client_ID());

			MLocation lm = new MLocation(getCtx(), c_location_matriz_id, get_TrxName());

			// Prestador del Servicio
			MBPartner bp = new MBPartner(getCtx(), getC_BPartner_ID(), get_TrxName());

			m_razonsocial = bp.getName();

			X_LCO_TaxIdType ttc = new X_LCO_TaxIdType(getCtx(), (Integer) bp.get_Value("LCO_TaxIdType_ID"),
					get_TrxName());

			m_tipoidentificacioncomprador = LEC_FE_Utils
					.getTipoIdentificacionSri(ttc.get_Value("LEC_TaxCodeSRI").toString());

			m_identificacioncomprador = bp.getTaxID();

			X_LCO_TaxIdType tt = new X_LCO_TaxIdType(getCtx(), (Integer) bp.get_Value("LCO_TaxIdType_ID"),
					get_TrxName());

			// Support ticket http://support.ingeint.com/issues/727
			m_totaldescuento = DB.getSQLValueBD(get_TrxName(),
					"SELECT COALESCE(SUM(il.DiscountAmt), 0) FROM c_invoiceLine il WHERE il.C_Invoice_ID = ? ",
					getC_Invoice_ID());

			if (m_totaldescuento == null)
				m_totaldescuento = Env.ZERO;			
			
			m_withholdingamt = (new BigDecimal(get_ValueAsString("WithholdingAmt")));
			if (m_withholdingamt == null)
				m_withholdingamt = Env.ZERO;

			//
			int sri_accesscode_id = 0;

			// New/Upd Access Code

			X_SRI_AccessCode ac = null;
			ac = new X_SRI_AccessCode(getCtx(), sri_accesscode_id, get_TrxName());
			ac.setAD_Org_ID(getAD_Org_ID());
			ac.setOldValue(null); // Deprecated
			ac.setEnvType(signature.getEnvType());
			ac.setCodeAccessType(signature.getCodeAccessType());
			ac.setSRI_ShortDocType(m_coddoc);
			ac.setIsUsed(true);
			ac.saveEx();

			 // Access Code
            m_accesscode = LEC_FE_Utils.getAccessCode(getDateInvoiced(), m_coddoc, bpe.getTaxID(),
                    //oi.get_ValueAsString("SRI_OrgCode"), 
                    LEC_FE_Utils.getOrgCode(LEC_FE_Utils.formatDocNo(getDocumentNo(), m_coddoc)), 
                    LEC_FE_Utils.getStoreCode(LEC_FE_Utils.formatDocNo(getDocumentNo(), m_coddoc)), 
                    getDocumentNo(),
                    oi.get_ValueAsString("SRI_DocumentCode"), signature.getDeliveredType(), ac);

			if (signature.getCodeAccessType().equals(LEC_FE_UtilsXml.claveAccesoAutomatica))
				ac.setValue(m_accesscode);

			if (!ac.save()) {
				msg = "@SaveError@ No se pudo grabar SRI Access Code";
				return "Error en Liquidación No " + getDocumentNo() + " " + msg;
			}

			// New Authorization

			a = new X_SRI_Authorization(getCtx(), 0, get_TrxName());

			a.setAD_Org_ID(getAD_Org_ID());
			a.setSRI_ShortDocType(m_coddoc);
			a.setValue(m_accesscode);
			a.setSRI_AccessCode_ID(ac.get_ID());
			a.setSRI_ErrorCode_ID(0);
			a.setAD_UserMail_ID(getAD_User_ID());
			a.set_ValueOfColumn("isSRIOfflineSchema", isOfflineSchema);
			a.set_ValueOfColumn("C_Invoice_ID", get_ID());

			OutputStream mmDocStream = null;

			String xmlFileName = "SRI_" + m_coddoc + "-" + LEC_FE_Utils.getDate(getDateInvoiced(), 9) + "-"
					+ m_accesscode + ".xml";

			// ruta completa del archivo xml
			file_name = signature.getFolderRaiz() + File.separator + LEC_FE_UtilsXml.folderComprobantesGenerados
					+ File.separator + xmlFileName;
			// Stream para el documento xml
			mmDocStream = new FileOutputStream(file_name, false);
			StreamResult streamResult_menu = new StreamResult(
					new OutputStreamWriter(mmDocStream, signature.getXmlEncoding()));
			SAXTransformerFactory tf_menu = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
			try {
				tf_menu.setAttribute("indent-number", new Integer(0));
			} catch (Exception e) {
				// swallow
			}
			TransformerHandler mmDoc = tf_menu.newTransformerHandler();
			Transformer serializer_menu = mmDoc.getTransformer();
			serializer_menu.setOutputProperty(OutputKeys.ENCODING, signature.getXmlEncoding());
			try {
				serializer_menu.setOutputProperty(OutputKeys.INDENT, "yes");
			} catch (Exception e) {
				// swallow
			}
			mmDoc.setResult(streamResult_menu);

			mmDoc.startDocument();

			AttributesImpl atts = new AttributesImpl();

			StringBuffer sql = null;

			// Encabezado
			atts.clear();
			atts.addAttribute("", "", "id", "CDATA", "comprobante");
			atts.addAttribute("", "", "version", "CDATA", f.get_ValueAsString("VersionNo"));
			mmDoc.startElement("", "", f.get_ValueAsString("XmlPrintLabel"), atts);
			atts.clear();

			// Emisor
			mmDoc.startElement("", "", "infoTributaria", atts);
			// Numerico1
			addHeaderElement(mmDoc, "ambiente", signature.getEnvType(), atts);
			// Numerico1
			addHeaderElement(mmDoc, "tipoEmision", signature.getDeliveredType(), atts);
			// Alfanumerico Max 300
			addHeaderElement(mmDoc, "razonSocial", bpe.getName(), atts);
			// Alfanumerico Max 300
			addHeaderElement(mmDoc, "nombreComercial", bpe.getName2() == null ? bpe.getName() : bpe.getName2(), atts);
			// Numerico13
			addHeaderElement(mmDoc, "ruc",
					(LEC_FE_Utils.fillString(13 - (LEC_FE_Utils.cutString(bpe.getTaxID(), 13)).length(), '0'))
							+ LEC_FE_Utils.cutString(bpe.getTaxID(), 13),
					atts);
			// Numérico49
			addHeaderElement(mmDoc, "claveAcceso", a.getValue(), atts);
			// Numerico2
			addHeaderElement(mmDoc, "codDoc", m_coddoc, atts);
			// Numerico3
			addHeaderElement(mmDoc, "estab", getDocumentNo().substring(0, 3), atts);
			// Numerico3
			addHeaderElement(mmDoc, "ptoEmi",
					LEC_FE_Utils.getStoreCode(LEC_FE_Utils.formatDocNo(getDocumentNo(), m_coddoc)), atts);
			// Numerico9
			addHeaderElement(mmDoc, "secuencial", (LEC_FE_Utils.fillString(
					9 - (LEC_FE_Utils.cutString(LEC_FE_Utils.getSecuencial(getDocumentNo(), m_coddoc), 9)).length(),
					'0')) + LEC_FE_Utils.cutString(LEC_FE_Utils.getSecuencial(getDocumentNo(), m_coddoc), 9), atts);
			// dirMatriz ,Alfanumerico Max 300
			addHeaderElement(mmDoc, "dirMatriz", lm.getAddress1(), atts);
			mmDoc.endElement("", "", "infoTributaria");

			mmDoc.startElement("", "", "infoLiquidacionCompra", atts);
			// Emisor
			// Fecha8 ddmmaaaa
			addHeaderElement(mmDoc, "fechaEmision", LEC_FE_Utils.getDate(getDateInvoiced(), 10), atts);
			// Alfanumerico Max 300
			addHeaderElement(mmDoc, "dirEstablecimiento", lo.getAddress1(), atts);
			// Numerico3-5
			addHeaderElement(mmDoc, "contribuyenteEspecial", oi.get_ValueAsString("SRI_TaxPayerCode"), atts);
			// Texto2
			addHeaderElement(mmDoc, "obligadoContabilidad", m_obligadocontabilidad, atts);

			// Comprador
			// Numerico2
			addHeaderElement(mmDoc, "tipoIdentificacionProveedor", m_tipoidentificacioncomprador, atts);

			// Alfanumerico Max 300
			addHeaderElement(mmDoc, "razonSocialProveedor", m_razonsocial, atts);
			// Numerico Max 13
			addHeaderElement(mmDoc, "identificacionProveedor", m_identificacioncomprador, atts);
			// Alfanumerico Max 300

			// Numerico Max 14
			addHeaderElement(mmDoc, "totalSinImpuestos", getTotalLines().toString(), atts);
			// Alfanumerico Max 10

			// Numerico MAx 14
			addHeaderElement(mmDoc, "totalDescuento", m_totaldescuento.toString(), atts);

			// Impuestos
			mmDoc.startElement("", "", "totalConImpuestos", atts);

			sql = new StringBuffer("SELECT COALESCE(tc.SRI_TaxCodeValue, '0') AS codigo "
					+ ", COALESCE(tr.SRI_TaxRateValue, 'X') AS codigoPorcentaje "
					+ ", SUM(it.TaxBaseAmt) AS baseImponible " + ", SUM(it.TaxAmt) AS valor "
					+ ", 0::numeric AS descuentoAdicional " + "FROM C_Invoice i "
					+ "JOIN C_InvoiceTax it ON it.C_Invoice_ID = i.C_Invoice_ID "
					+ "JOIN C_Tax t ON t.C_Tax_ID = it.C_Tax_ID "
					+ "JOIN C_TaxCategory tca on t.C_TaxCategory_ID = tca.C_TaxCategory_ID "
					+ "LEFT JOIN SRI_TaxCode tc ON t.SRI_TaxCode_ID = tc.SRI_TaxCode_ID "
					+ "LEFT JOIN SRI_TaxRate tr ON t.SRI_TaxRate_ID = tr.SRI_TaxRate_ID "
					+ "WHERE i.C_Invoice_ID = ? AND tca.IsWithholding = 'N' " + "GROUP BY codigo, codigoPorcentaje "
					+ "ORDER BY codigo, codigoPorcentaje ");
			try {
				PreparedStatement pstmt = DB.prepareStatement(sql.toString(), get_TrxName());
				pstmt.setInt(1, getC_Invoice_ID());
				ResultSet rs = pstmt.executeQuery();
				//

				while (rs.next()) {
					if (rs.getString(1).equals("0") || rs.getString(2).equals("X")) {
						msg = "Impuesto sin Tipo ó Porcentaje impuesto SRI";
						return "Error en Factura No " + getDocumentNo() + " " + msg;
					}

					mmDoc.startElement("", "", "totalImpuesto", atts);

					// Numerico 1
					addHeaderElement(mmDoc, "codigo", rs.getString(1), atts);
					// Numerico 1 to 4
					addHeaderElement(mmDoc, "codigoPorcentaje", rs.getString(2), atts);

					// Numerico Max 14
					addHeaderElement(mmDoc, "baseImponible", rs.getBigDecimal(3).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "valor", rs.getBigDecimal(4).toString(), atts);

					mmDoc.endElement("", "", "totalImpuesto");

					m_totalbaseimponible = m_totalbaseimponible.add(rs.getBigDecimal(3));
					m_totalvalorimpuesto = m_totalvalorimpuesto.add(rs.getBigDecimal(4));

				}
				rs.close();
				pstmt.close();
			} catch (SQLException e) {
				log.log(Level.SEVERE, sql.toString(), e);
				msg = "Error SQL: " + sql.toString();
				return "Error en Liquidación de Compras No " + getDocumentNo() + " " + msg;
			}

			mmDoc.endElement("", "", "totalConImpuestos");

			// Numerico Max 14
			addHeaderElement(mmDoc, "importeTotal", getGrandTotal().add(m_withholdingamt).toString(), atts);
			// Alfanumerico MAx 25
			addHeaderElement(mmDoc, "moneda", getCurrencyISO(), atts);

			// support ticket http://support.ingeint.com/issues/774

			mmDoc.startElement("", "", "pagos", atts);

			MInvoicePaySchedule[] ipss = MInvoicePaySchedule.getInvoicePaySchedule(getCtx(), get_ID(), 0,
					get_TrxName());
			MInvoice invoice = new MInvoice(getCtx(), get_ID(), get_TrxName());

			int rows = ipss.length;

			if (rows == 0) {
				MPaymentTerm paymentTerm = (MPaymentTerm) getC_PaymentTerm();
				mmDoc.startElement("", "", "pago", atts);
				addHeaderElement(mmDoc, "formaPago", invoice.get_ValueAsString("LEC_PaymentMethod"), atts);
				addHeaderElement(mmDoc, "total", getGrandTotal().add(m_withholdingamt).toString(), atts);
				addHeaderElement(mmDoc, "plazo", String.valueOf(paymentTerm.getNetDays()), atts);
				addHeaderElement(mmDoc, "unidadTiempo", "dias", atts);
				mmDoc.endElement("", "", "pago");

			} else {

				for (int i = 0; i < rows; i++)

				{
					mmDoc.startElement("", "", "pago", atts);
					addHeaderElement(mmDoc, "formaPago", ipss[i].get_ValueAsString("LEC_PaymentMethod"), atts);
					addHeaderElement(mmDoc, "total", LEC_FE_Utils.cutString(ipss[i].getDueAmt().toString(), 14), atts);
					long Daysms = 0;
					Daysms = (ipss[i].getDueDate().getTime() - invoice.getDateAcct().getTime());
					long NetDays = Daysms / (1000 * 60 * 60 * 24);
					addHeaderElement(mmDoc, "plazo", LEC_FE_Utils.cutString(String.valueOf(NetDays), 14), atts);
					addHeaderElement(mmDoc, "unidadTiempo", "dias", atts);
					mmDoc.endElement("", "", "pago");
				}
			}

			mmDoc.endElement("", "", "pagos");

			mmDoc.endElement("", "", "infoLiquidacionCompra");

			// Detalles
			mmDoc.startElement("", "", "detalles", atts);
			// Support ticket http://support.ingeint.com/issues/727
			// Added discountAmt

			if (dt.get_ValueAsBoolean("IsSummaryInvoice")) {

				sql = new StringBuffer(
						"SELECT i.C_Invoice_ID, COALESCE(p.value, c.name), (case  WHEN p.m_product_id isnull THEN '0' when p.upc isnull or p.upc ='' then p.value else p.upc END), "
								+ " COALESCE(ilt.name,c.name), sum(ilt.QtyEntered), ROUND(ilt.PriceEntered,6), COALESCE(il.discountAmt,0) AS discount "
								+ ",(sum(ilt.linenetamt) - COALESCE(sum(il.discountAmt),0)) as linenetamt  \n"
								+ ", COALESCE(tc.SRI_TaxCodeValue, '0') AS codigo \n"
								+ ", COALESCE(tr.SRI_TaxRateValue, 'X') AS codigoPorcentaje \n"
								+ ", t.rate AS tarifa \n"
								+ ", (sum(ilt.linenetamt) - COALESCE(sum(il.discountAmt),0)) AS baseImponible \n"
								+ ", ROUND((sum(ilt.linenetamt) - COALESCE(sum(il.discountAmt),0)) * t.rate / 100, 2) AS valor  \n"
								+ ", COALESCE(il.description,'') AS description1 \n"
								+ ", 0::numeric AS descuentoAdicional \n" + ", il.C_UOM_ID \n" + "FROM C_Invoice i \n"
								+ "JOIN C_InvoiceLine il ON il.C_Invoice_ID = i.C_Invoice_ID \n"
								+ "JOIN C_Invoice_LineTax_VT ilt ON ilt.C_Invoice_ID = i.C_Invoice_ID AND ilt.C_InvoiceLine_ID = il.C_InvoiceLine_ID \n"
								+ "JOIN C_Tax t ON t.C_Tax_ID = ilt.C_Tax_ID \n"
								+ "LEFT JOIN SRI_TaxCode tc ON t.SRI_TaxCode_ID = tc.SRI_TaxCode_ID \n"
								+ "LEFT JOIN SRI_TaxRate tr ON t.SRI_TaxRate_ID = tr.SRI_TaxRate_ID \n"
								+ "LEFT JOIN M_Product p ON p.M_Product_ID = il.M_Product_ID \n"
								+ "LEFT JOIN M_Product_Category pc ON pc.M_Product_Category_ID = p.M_Product_Category_ID \n"
								+ "LEFT JOIN C_Charge c ON il.C_Charge_ID = c.C_Charge_ID AND c.IsInternal ='N' \n"
								+ "WHERE il.IsDescription = 'N' AND i.C_Invoice_ID=? and il.linenetamt>=0 \n"
								+ "GROUP By p.value, c.name, (case  WHEN p.m_product_id isnull THEN '0' when p.upc isnull or p.upc ='' then p.value else p.upc END),\n"
								+ "ilt.name, i.c_invoice_id, ilt.priceentered, il.discountamt,ilt.linenetamt, tc.SRI_TaxCodeValue, tr.SRI_TaxRateValue, t.rate, il.description,\n"
								+ "il.c_uom_id ");
			} else {
				sql = new StringBuffer(
						"SELECT i.C_Invoice_ID, COALESCE(p.value, '0'), (case  WHEN p.m_product_id isnull THEN '0' when p.upc isnull or p.upc ='' then p.value else p.upc END),"
								+ " ilt.name, ilt.QtyEntered, ROUND(ilt.PriceEntered,6), COALESCE(il.discountAmt,0) AS discount "
								+ ",(ilt.linenetamt - COALESCE(il.discountAmt,0)) as linenetamt  "
								+ ", COALESCE(tc.SRI_TaxCodeValue, '0') AS codigo "
								+ ", COALESCE(tr.SRI_TaxRateValue, 'X') AS codigoPorcentaje " + ", t.rate AS tarifa "
								+ ", (ilt.linenetamt - COALESCE(il.discountAmt,0)) AS baseImponible "
								+ ", ROUND((ilt.linenetamt - COALESCE(il.discountAmt,0)) * t.rate / 100, 2) AS valor  "
								+ ", COALESCE(il.description,'') AS description1 "
								+ ", 0::numeric AS descuentoAdicional " + ", il.C_UOM_ID " + "FROM C_Invoice i "
								+ "JOIN C_InvoiceLine il ON il.C_Invoice_ID = i.C_Invoice_ID "
								+ "JOIN C_Invoice_LineTax_VT ilt ON ilt.C_Invoice_ID = i.C_Invoice_ID AND ilt.C_InvoiceLine_ID = il.C_InvoiceLine_ID "
								+ "JOIN C_Tax t ON t.C_Tax_ID = ilt.C_Tax_ID "
								+ "LEFT JOIN SRI_TaxCode tc ON t.SRI_TaxCode_ID = tc.SRI_TaxCode_ID "
								+ "LEFT JOIN SRI_TaxRate tr ON t.SRI_TaxRate_ID = tr.SRI_TaxRate_ID "
								+ "LEFT JOIN M_Product p ON p.M_Product_ID = il.M_Product_ID "
								+ "LEFT JOIN M_Product_Category pc ON pc.M_Product_Category_ID = p.M_Product_Category_ID "
								+ "LEFT JOIN C_Charge c ON il.C_Charge_ID = c.C_Charge_ID AND c.IsInternal ='N' "
								+ "WHERE il.IsDescription = 'N' AND i.C_Invoice_ID=? and il.linenetamt>=0 and il.TaxAmt >= 0 "
								+ "ORDER BY il.line");

			}
			try {
				PreparedStatement pstmt = DB.prepareStatement(sql.toString(), get_TrxName());
				pstmt.setInt(1, getC_Invoice_ID());
				ResultSet rs = pstmt.executeQuery();
				//

				while (rs.next()) {

					mmDoc.startElement("", "", "detalle", atts);

					// Alfanumerico MAx 25
					addHeaderElement(mmDoc, "codigoPrincipal", LEC_FE_Utils.cutString(rs.getString(2), 25), atts);
					// Alfanumerico MAx 25
					addHeaderElement(mmDoc, "codigoAuxiliar", LEC_FE_Utils.cutString(rs.getString(3), 25), atts);
					// Alfanumerico Max 300
					addHeaderElement(mmDoc, "descripcion", LEC_FE_Utils.cutString(rs.getString(4), 300), atts);
					// Alfanumerico Max 50

					// Numerico Max 14
					addHeaderElement(mmDoc, "cantidad", rs.getBigDecimal(5).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "precioUnitario", rs.getBigDecimal(6).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "descuento", rs.getBigDecimal(7).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "precioTotalSinImpuesto", rs.getBigDecimal(8).toString(), atts);

					mmDoc.startElement("", "", "detallesAdicionales", atts);

					String ad_language = Env.getAD_Language(getCtx());
					boolean isBaseLanguage = Env.isBaseLanguage(ad_language, "AD_Ref_List");
					String UOMTable = isBaseLanguage ? "C_UOM" : "C_UOM_Trl";
					Object UOMNameOb = new Query(getCtx(), UOMTable, "C_UOM_ID = ?", null)
							.setParameters(rs.getBigDecimal(16)).first().get_ValueAsString("Name");
					if (UOMNameOb == null) {
						msg = "Error en Nombre de Unidad de Medida";
						log.log(Level.SEVERE, msg);
						return "Error en Factura No " + getDocumentNo() + " " + msg;
					}

					atts.clear();
					atts.addAttribute("", "", "nombre", "CDATA", "UMedida");
					atts.addAttribute("", "", "valor", "CDATA", LEC_FE_Utils.cutString(UOMNameOb.toString(), 50));
					mmDoc.startElement("", "", "detAdicional", atts);
					mmDoc.endElement("", "", "detAdicional");
					mmDoc.endElement("", "", "detallesAdicionales");

					atts.clear();
					//
					mmDoc.startElement("", "", "impuestos", atts);
					if (rs.getString(9).equals("0") || rs.getString(10).equals("X")) {
						msg = "Impuesto sin Tipo ó Porcentaje impuesto SRI";
						return "Error en Factura No " + getDocumentNo() + " " + msg;
					}

					mmDoc.startElement("", "", "impuesto", atts);
					// Numerico 1
					addHeaderElement(mmDoc, "codigo", rs.getString(9), atts);
					// Numerico 1 to 4
					addHeaderElement(mmDoc, "codigoPorcentaje", rs.getString(10), atts);
					// Numerico 1 to 4
					addHeaderElement(mmDoc, "tarifa", rs.getBigDecimal(11).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "baseImponible", rs.getBigDecimal(12).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "valor", rs.getBigDecimal(13).toString(), atts);
					mmDoc.endElement("", "", "impuesto");
					mmDoc.endElement("", "", "impuestos");

					mmDoc.endElement("", "", "detalle");

					m_sumadescuento = m_sumadescuento.add(rs.getBigDecimal(7));
					m_sumabaseimponible = m_sumabaseimponible.add(rs.getBigDecimal(12));
					m_sumavalorimpuesto = m_sumavalorimpuesto.add(rs.getBigDecimal(13));

				}
				rs.close();
				pstmt.close();
			} catch (SQLException e) {
				log.log(Level.SEVERE, sql.toString(), e);
				msg = "Error SQL: " + sql.toString();
				return "Error en Liquidación No " + getDocumentNo() + " " + msg;
			}

			String valor = "";
			mmDoc.endElement("", "", "detalles");
			mmDoc.startElement("", "", "infoAdicional", atts);
			if (getDescription() != null) {

				atts.clear();
				atts.addAttribute("", "", "nombre", "CDATA", "Descripcion");
				mmDoc.startElement("", "", "campoAdicional", atts);
				valor = LEC_FE_Utils.cutString(getDescription(), 300);
				mmDoc.characters(valor.toCharArray(), 0, valor.length());
				mmDoc.endElement("", "", "campoAdicional");
			}

			atts.addAttribute("", "", "nombre", "CDATA", "Direccion Cliente");
			mmDoc.startElement("", "", "campoAdicional", atts);
			StringBuffer addr = new StringBuffer();
			addr.append(getC_BPartner_Location().getC_Location().getAddress1() + " ");
			addr.append(getC_BPartner_Location().getC_Location().getAddress2() == null ? ""
					: getC_BPartner_Location().getC_Location().getAddress2() + " ");
			addr.append(getC_BPartner_Location().getC_Location().getCity() == null ? ""
					: getC_BPartner_Location().getC_Location().getCity() + "-");
			addr.append(getC_BPartner_Location().getC_Location().getRegionName() == null ? ""
					: getC_BPartner_Location().getC_Location().getRegionName());
			addr.append(getC_BPartner_Location().getC_Location().getC_Country().getName() == null ? ""
					: "," + getC_BPartner_Location().getC_Location().getC_Country().getName());
			valor = addr.toString();

			mmDoc.characters(valor.toCharArray(), 0, valor.length());
			mmDoc.endElement("", "", "campoAdicional");

			if (getPOReference() != null) {
				if (!getPOReference().trim().isEmpty()) {
					atts.addAttribute("", "", "nombre", "CDATA", "Orden de Compra");
					mmDoc.startElement("", "", "campoAdicional", atts);
					String valoroc = LEC_FE_Utils.cutString(getPOReference(), 300);
					mmDoc.characters(valoroc.toCharArray(), 0, valoroc.length());
					mmDoc.endElement("", "", "campoAdicional");
				}
			}

			atts.addAttribute("", "", "nombre", "CDATA", "Contacto");
			mmDoc.startElement("", "", "campoAdicional", atts);
			StringBuffer user = new StringBuffer();
			user.append(getAD_User().getName());
			user.append(" ");
			user.append(getAD_User().getPhone() == null ? " " : "TELEFONO: " + getAD_User().getPhone());
			user.append(" ");
			user.append(getAD_User().getEMail() == null ? " " : "E-MAIL: " + getAD_User().getEMail());

			valor = user.toString();
			mmDoc.characters(valor.toCharArray(), 0, valor.length());
			mmDoc.endElement("", "", "campoAdicional");

			atts.addAttribute("", "", "nombre", "CDATA", "Vendedor");
			mmDoc.startElement("", "", "campoAdicional", atts);
			StringBuffer srep = new StringBuffer();
			srep.append(getSalesRep().getName());
			srep.append(" ");
			srep.append(getSalesRep().getEMail() == null ? "" : getSalesRep().getEMail());
			valor = LEC_FE_Utils.cutString(srep.toString(), 300);
			mmDoc.characters(valor.toCharArray(), 0, valor.length());
			mmDoc.endElement("", "", "campoAdicional");

			if (getC_Order_ID() > 0) {
				atts.addAttribute("", "", "nombre", "CDATA", "Orden de Venta:");
				mmDoc.startElement("", "", "campoAdicional", atts);
				StringBuffer ov = new StringBuffer();
				ov.append(getC_Order().getDocumentNo());
				ov.append(" ");
				valor = LEC_FE_Utils.cutString(ov.toString(), 300);
				mmDoc.characters(valor.toCharArray(), 0, valor.length());
				mmDoc.endElement("", "", "campoAdicional");
			}

			mmDoc.endElement("", "", "infoAdicional");
			mmDoc.endElement("", "", f.get_ValueAsString("XmlPrintLabel"));
			mmDoc.endDocument();

			if (mmDocStream != null) {
				try {
					mmDocStream.close();
				} catch (Exception e2) {
				}
			}

			if (m_sumadescuento.compareTo(m_totaldescuento) != 0) {
				msg = "Error Diferencia Descuento Total: " + m_totaldescuento.toString() + " Detalles: "
						+ m_sumadescuento.toString();
				return "Error en Factura No " + getDocumentNo() + " " + msg;
			}

			if (m_sumabaseimponible.compareTo(m_totalbaseimponible) != 0
					&& m_totalbaseimponible.subtract(m_sumabaseimponible).abs().compareTo(LEC_FE_UtilsXml.HALF) > 1) {
				msg = "Error Diferencia Base Impuesto Total: " + m_totalbaseimponible.toString() + " Detalles: "
						+ m_sumabaseimponible.toString();
				return "Error en Factura No " + getDocumentNo() + " " + msg;
			}

			if (m_sumavalorimpuesto.compareTo(m_totalvalorimpuesto) != 0
					&& m_totalvalorimpuesto.subtract(m_sumavalorimpuesto).abs().compareTo(LEC_FE_UtilsXml.HALF) > 1) {
				msg = "Error Diferencia Impuesto Total: " + m_totalvalorimpuesto.toString() + " Detalles: "
						+ m_sumavalorimpuesto.toString();
				return "Error en Factura No " + getDocumentNo() + " " + msg;
			}

			// if (LEC_FE_Utils.breakDialog("Firmando Xml")) return "Cancelado..."; // TODO
			// Temp

			log.warning("@Signing Xml@ -> " + file_name);
			signature.setResource_To_Sign(file_name);
			signature.setOutput_Directory(
					signature.getFolderRaiz() + File.separator + LEC_FE_UtilsXml.folderComprobantesFirmados);
			signature.execute();

			file_name = signature.getFilename(signature, LEC_FE_UtilsXml.folderComprobantesFirmados);

			if (dt.get_Value("IsGenerateInBatch") != null)
				IsGenerateInBatch = dt.get_ValueAsBoolean("IsGenerateInBatch");
			if (!signature.IsUseContingency && !IsGenerateInBatch) {

				// Procesar Recepcion SRI
				log.warning("@Sending Xml@ -> " + file_name);
				msg = signature.respuestaRecepcionComprobante(file_name);

				if (msg != null)
					if (!msg.equals("RECIBIDA")) {

						DB.executeUpdate("DELETE FROM SRI_Authorization WHERE SRI_Authorization_ID = " + a.get_ID(),
								true, get_TrxName());

						int exist = DB.getSQLValue(null,
								"SELECT Record_id FROM AD_Note WHERE AD_Table_ID = 318 AND Record_ID= ? ", get_ID());

						if (exist <= 0) {
							MNote note = new MNote(getCtx(), 0, null);
							note.setAD_Table_ID(MInvoice.Table_ID);
							note.setReference("Error en Factura de venta, por favor valide la info del documento: "
									+ getDocumentNo());
							note.setAD_Org_ID(getAD_Org_ID());
							note.setTextMsg(msg);
							note.setAD_Message_ID("ErrorFE");
							note.setRecord(MInvoice.Table_ID, getC_Invoice_ID());
							note.setAD_User_ID(MSysConfig.getIntValue("ING_FEUserNotes", 100, getAD_Client_ID()));
							note.setDescription(getDocumentNo());
							set_Value("IsSRI_Error", true);
							note.saveEx();
							invoice.set_ValueOfColumn("IsSriError", true);
							invoice.saveEx();
						}

						// TODO Agregar al OffLine el error Maybe ADNote
						return "Error en Liquidación No " + getDocumentNo() + " " + msg;
					}
				String invoiceNo = getDocumentNo();
				String invoiceID = String.valueOf(get_ID());
				a.setDescription(invoiceNo);
				a.set_ValueOfColumn("DocumentID", invoiceID);
				a.set_ValueOfColumn("C_Invoice_ID", get_ID());
				a.saveEx();
				msg = null;
				file_name = signature.getFilename(signature, LEC_FE_UtilsXml.folderComprobantesEnProceso);

			} else { // emisionContingencia
				// Completar en estos casos, luego usar Boton Procesar
				// 170-Clave de contingencia pendiente
				if (signature.IsUseContingency)
					a.setSRI_ErrorCode_ID(LEC_FE_Utils.getErrorCode("170"));
				// 70-Clave de acceso en procesamiento
				if (IsGenerateInBatch)
					a.setSRI_ErrorCode_ID(LEC_FE_Utils.getErrorCode("70"));
				a.saveEx();

				if (signature.isAttachXml())
					LEC_FE_Utils.attachXmlFile(a.getCtx(), a.get_TrxName(), a.getSRI_Authorization_ID(), file_name);

			}
			log.warning("@SRI_FileGenerated@ -> " + file_name);

			//
		} catch (Exception e) {
			msg = "No se pudo crear XML - " + e.getMessage();
			log.severe(msg);
			return "Error en Factura No " + getDocumentNo() + " " + msg;
		} catch (Error e) {
			msg = "No se pudo crear XML- Error en Conexion con el SRI " + e;
			return "Error en Factura No " + getDocumentNo() + " " + msg;
		}

		set_Value("SRI_AuthorizationPL_ID", a.get_ID());

		MDocType dt = new MDocType(getCtx(), getC_DocType_ID(), get_TrxName());

		if (!dt.get_Value("GenerateWithholding").equals("N")) {

			LEC_FE_MRetencion lecfeinvret = new LEC_FE_MRetencion(getCtx(), getC_Invoice_ID(), get_TrxName());
			if (lecfeinvret.get_ValueAsInt("SRI_Authorization_ID") < 1 && MSysConfig
					.getBooleanValue("LEC_GenerateWitholdingToComplete", false, lecfeinvret.getAD_Client_ID())) {
				LEC_FE_MRetencion.generateWitholdingNo(lecfeinvret);
				msg = lecfeinvret.lecfeinvret_SriExportRetencionXML100();
			}
		} else
			set_Value("SRI_Authorization_ID", a.get_ID());

		this.saveEx();
		return msg;

	} // lecfeinv_SriExportInvoiceXML100

	public void addHeaderElement(TransformerHandler mmDoc, String att, String value, AttributesImpl atts)
			throws Exception {
		if (att != null) {
			mmDoc.startElement("", "", att, atts);
			mmDoc.characters(value.toCharArray(), 0, value.toCharArray().length);
			mmDoc.endElement("", "", att);
		} else {
			throw new AdempiereUserError(att + " empty");
		}
	}

	public void addElementsToExportInvoice() {

	}

} // LEC_FE_MInvoice
