package org.globalqss.model;

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
import org.compiere.model.MLocation;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MSysConfig;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.globalqss.util.LEC_FE_Utils;
import org.globalqss.util.LEC_FE_UtilsXml;
import org.xml.sax.helpers.AttributesImpl;

/**
 * LEC_FE_MInvoice
 *
 * @author Carlos Ruiz - globalqss - Quality Systems & Solutions -
 *         http://globalqss.com
 * @version $Id: LEC_FE_MNotaCredito.java,v 1.0 2014/05/06 03:37:29 cruiz Exp $
 */
public class LEC_FE_MNotaDebito extends MInvoice {
	/**
	 * 
	 */
	private static final long serialVersionUID = -924606040343895114L;

	private int m_lec_sri_format_id = 0;
	private int m_c_invoice_sus_id = 0;

	private String file_name = "";
	private String m_obligadocontabilidad = "NO";
	private String m_coddoc = "";
	private String m_accesscode;
	private String m_identificacionconsumidor = "";
	private String m_tipoidentificacioncomprador = "";
	private String m_identificacioncomprador = "";
	private String m_razonsocial = "";

	private BigDecimal m_totaldescuento = Env.ZERO;
	private BigDecimal m_totalbaseimponible = Env.ZERO;
	private BigDecimal m_totalvalorimpuesto = Env.ZERO;
	private BigDecimal m_sumadescuento = Env.ZERO;
	private BigDecimal m_sumabaseimponible = Env.ZERO;
	private BigDecimal m_sumavalorimpuesto = Env.ZERO;

	// 04/07/2016 MHG Offline Schema added
	private boolean isOfflineSchema = false;

	public LEC_FE_MNotaDebito(Properties ctx, int C_Invoice_ID, String trxName) {
		super(ctx, C_Invoice_ID, trxName);
		// 04/07/2016 MHG Offline Schema added
		isOfflineSchema = MSysConfig.getBooleanValue("QSSLEC_FE_OfflineSchema", false,
				Env.getAD_Client_ID(Env.getCtx()));
	}

	public String lecfeinvnd_SriExportNotaDebitoXML100() {
		LEC_FE_MNotaDebito debit = new LEC_FE_MNotaDebito(getCtx(), getC_Invoice_ID(), get_TrxName());
		X_SRI_Authorization a = new X_SRI_Authorization(getCtx(), debit.get_ValueAsInt("SRI_Authorization_ID"), get_TrxName());
		m_accesscode = a.getSRI_AccessCode().getValue();
		String msgStatus = "";

		String msg = null;
		String ErrorDocumentno = "Error en Nota de Débito No " + getDocumentNo() + " ";

		LEC_FE_UtilsXml signature = new LEC_FE_UtilsXml();

		try {
			// log.log(Level.WARNING, "Documento a procesar: "+getDocumentNo());
			signature.setAD_Org_ID(getAD_Org_ID());

			m_identificacionconsumidor = MSysConfig.getValue("QSSLEC_FE_IdentificacionConsumidorFinal", null,
					getAD_Client_ID());

			m_razonsocial = MSysConfig.getValue("QSSLEC_FE_RazonSocialPruebas", null, getAD_Client_ID());

			signature.setPKCS12_Resource(
					MSysConfig.getValue("QSSLEC_FE_RutaCertificadoDigital", null, getAD_Client_ID(), getAD_Org_ID()));
			signature.setPKCS12_Password(
					MSysConfig.getValue("QSSLEC_FE_ClaveCertificadoDigital", null, getAD_Client_ID(), getAD_Org_ID()));

			if (signature.getFolderRaiz() == null)
				return ErrorDocumentno + "No existe parametro para Ruta Generacion Xml";

			MDocType dt = new MDocType(getCtx(), getC_DocTypeTarget_ID(), get_TrxName());

			m_coddoc = dt.get_ValueAsString("SRI_ShortDocType");

			if (m_coddoc.equals(""))
				return ErrorDocumentno + "No existe definicion SRI_ShortDocType: " + dt.toString();

			// Formato
			m_lec_sri_format_id = LEC_FE_Utils.getLecSriFormat(getAD_Client_ID(), signature.getDeliveredType(),
					m_coddoc, getDateInvoiced(), getDateInvoiced());

			if (m_lec_sri_format_id < 1)
				return ErrorDocumentno + "No existe formato para el comprobante";

			X_LEC_SRI_Format f = new X_LEC_SRI_Format(getCtx(), m_lec_sri_format_id, get_TrxName());

			// Emisor
			MOrgInfo oi = MOrgInfo.get(getCtx(), getAD_Org_ID(), get_TrxName());

			msg = LEC_FE_ModelValidator.valideOrgInfoSri(oi);

			if (msg != null)
				return ErrorDocumentno + msg;

			if ((Boolean) oi.get_Value("SRI_IsKeepAccounting"))
				m_obligadocontabilidad = "SI";

			int c_bpartner_id = LEC_FE_Utils.getOrgBPartner(getAD_Client_ID(), oi.get_ValueAsString("TaxID"));
			MBPartner bpe = new MBPartner(getCtx(), c_bpartner_id, get_TrxName());

			MLocation lo = new MLocation(getCtx(), oi.getC_Location_ID(), get_TrxName());

			int c_location_matriz_id = MSysConfig.getIntValue("QSSLEC_FE_LocalizacionDireccionMatriz", -1,
					oi.getAD_Client_ID(), getAD_Org_ID());

			MLocation lm = new MLocation(getCtx(), c_location_matriz_id, get_TrxName());

			// Comprador
			msgStatus = "Partner";
			MBPartner bp = new MBPartner(getCtx(), getC_BPartner_ID(), get_TrxName());
			if (!signature.isOnTesting())
				m_razonsocial = bp.getName();

			msgStatus = "TaxIdType";
			X_LCO_TaxIdType ttc = new X_LCO_TaxIdType(getCtx(), (Integer) bp.get_Value("LCO_TaxIdType_ID"),
					get_TrxName());

			msgStatus = "TaxCodeSRI";
			m_tipoidentificacioncomprador = LEC_FE_Utils
					.getTipoIdentificacionSri(ttc.get_Value("LEC_TaxCodeSRI").toString());

			msgStatus = "TaxID";
			m_identificacioncomprador = bp.getTaxID();

			X_LCO_TaxIdType tt = new X_LCO_TaxIdType(getCtx(), (Integer) bp.get_Value("LCO_TaxIdType_ID"),
					get_TrxName());
			if (tt.getLCO_TaxIdType_ID() == 1000011) // Hardcoded F Final // TODO Deprecated
				m_identificacioncomprador = m_identificacionconsumidor;

			msgStatus = "TaxPayerType";
			X_LCO_TaxPayerType tp = new X_LCO_TaxPayerType(getCtx(), (Integer) bp.get_Value("LCO_TaxPayerType_ID"),
					get_TrxName());

			if (get_Value("Sri_RefInvoice_ID") == null)
				return ErrorDocumentno + "No existe documento sustento para el comprobante";

			msgStatus = "RefInvoice";
			m_c_invoice_sus_id = (Integer) get_Value("Sri_RefInvoice_ID");

			MInvoice invsus = new MInvoice(getCtx(), m_c_invoice_sus_id, get_TrxName());

			m_totaldescuento = Env.ZERO; 
				
			msgStatus = "AccessCode";
			
			X_SRI_AccessCode ac = new X_SRI_AccessCode(getCtx(), a.getSRI_AccessCode_ID(), get_TrxName());			

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
			addHeaderElement(mmDoc, "nombreComercial", bpe.getName2(), atts);
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

			mmDoc.startElement("", "", "infoNotaDebito", atts);
			// Emisor
			// Fecha8 ddmmaaaa
			addHeaderElement(mmDoc, "fechaEmision", LEC_FE_Utils.getDate(getDateInvoiced(), 10), atts);
			// Alfanumerico Max 300
			addHeaderElement(mmDoc, "dirEstablecimiento", lo.getAddress1(), atts);
			// Comprador
			// Numerico2
			addHeaderElement(mmDoc, "tipoIdentificacionComprador", m_tipoidentificacioncomprador, atts);
			// Alfanumerico Max 300
			addHeaderElement(mmDoc, "razonSocialComprador", m_razonsocial, atts);
			// Numerico Max 13
			addHeaderElement(mmDoc, "identificacionComprador", m_identificacioncomprador, atts);
			// Numerico3-5
			addHeaderElement(mmDoc, "contribuyenteEspecial", oi.get_ValueAsString("SRI_TaxPayerCode"), atts);
			// Texto2
			addHeaderElement(mmDoc, "obligadoContabilidad", m_obligadocontabilidad, atts);
			// Alfanumerico Max 40
			addHeaderElement(mmDoc, "rise", LEC_FE_Utils.cutString(tp.getName(), 40), atts);
			// Numerico2
			if (m_coddoc.equals("05"))
				addHeaderElement(mmDoc, "codDocModificado", "01", atts); // Hardcoded
			// Numerico15 -- Incluye guiones
			addHeaderElement(mmDoc, "numDocModificado", LEC_FE_Utils.formatDocNo(invsus.getDocumentNo(), "01"), atts);
			// Fecha8 ddmmaaaa
			addHeaderElement(mmDoc, "fechaEmisionDocSustento", LEC_FE_Utils.getDate(invsus.getDateInvoiced(), 10),
					atts);
			// Numerico Max 14
			addHeaderElement(mmDoc, "totalSinImpuestos", getTotalLines().toString(), atts);

			m_totalbaseimponible = getTotalLines();
			m_totalvalorimpuesto = getGrandTotal().subtract(getTotalLines()); // TODO Reviewme

			// Impuestos
			sql = new StringBuffer("SELECT COALESCE(tc.SRI_TaxCodeValue, '0') AS codigo "
					+ ", COALESCE(tr.SRI_TaxRateValue, 'X') AS codigoPorcentaje " + ", MIN(t.rate) AS tarifa "
					+ ", SUM(it.TaxBaseAmt) AS baseImponible " + ", SUM(it.TaxAmt) AS valor "
					+ ", MIN(t.name) AS razon " + ", 0::numeric AS descuentoAdicional " + "FROM C_Invoice i "
					+ "JOIN C_InvoiceTax it ON it.C_Invoice_ID = i.C_Invoice_ID "
					+ "JOIN C_Tax t ON t.C_Tax_ID = it.C_Tax_ID "
					+ "LEFT JOIN SRI_TaxCode tc ON t.SRI_TaxCode_ID = tc.SRI_TaxCode_ID "
					+ "LEFT JOIN SRI_TaxRate tr ON t.SRI_TaxRate_ID = tr.SRI_TaxRate_ID " + "WHERE i.C_Invoice_ID = ? "
					+ "GROUP BY codigo, codigoPorcentaje " + "ORDER BY codigo, codigoPorcentaje");

			try {
				PreparedStatement pstmt = DB.prepareStatement(sql.toString(), ResultSet.TYPE_SCROLL_INSENSITIVE,
						ResultSet.CONCUR_READ_ONLY, get_TrxName());
				pstmt.setInt(1, getC_Invoice_ID());
				ResultSet rs = pstmt.executeQuery();
				//

				mmDoc.startElement("", "", "impuestos", atts);

				while (rs.next()) {
					if (rs.getString(1).equals("0") || rs.getString(2).equals("X")) {
						msg = "Impuesto sin Tipo ó Porcentaje impuesto SRI";
						return ErrorDocumentno + msg;
					}

					mmDoc.startElement("", "", "impuesto", atts);
					// Numerico 1
					addHeaderElement(mmDoc, "codigo", rs.getString(1), atts);
					// Numerico 1 to 4
					addHeaderElement(mmDoc, "codigoPorcentaje", rs.getString(2), atts);
					// Numerico 1 to 4
					addHeaderElement(mmDoc, "tarifa", rs.getBigDecimal(3).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "baseImponible", rs.getBigDecimal(4).toString(), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "valor", rs.getBigDecimal(5).toString(), atts);
					mmDoc.endElement("", "", "impuesto");

					m_sumabaseimponible = m_sumabaseimponible.add(rs.getBigDecimal(4));
					m_sumavalorimpuesto = m_sumavalorimpuesto.add(rs.getBigDecimal(5));

				}

				mmDoc.endElement("", "", "impuestos");

				// Numerico Max 14
				addHeaderElement(mmDoc, "valorTotal", getGrandTotal().toString(), atts);

				mmDoc.endElement("", "", "infoNotaDebito");

				rs.beforeFirst();

				mmDoc.startElement("", "", "motivos", atts);

				while (rs.next()) {

					mmDoc.startElement("", "", "motivo", atts);
					// Alfanumerico MAx 300
					addHeaderElement(mmDoc, "razon", rs.getString(6), atts);
					// Numerico Max 14
					addHeaderElement(mmDoc, "valor", rs.getBigDecimal(4).toString(), atts);
					mmDoc.endElement("", "", "motivo");

				}

				mmDoc.endElement("", "", "motivos");

				rs.close();
				pstmt.close();
			} catch (SQLException e) {
				log.log(Level.SEVERE, sql.toString(), e);
				msg = "Error SQL: " + sql.toString();
				return ErrorDocumentno + msg;
			}

			if (oi.get_ValueAsBoolean("IsMicroBusiness")) {

				mmDoc.startElement("", "", "infoAdicional", atts);
				atts.clear();
				atts.addAttribute("", "", "nombre", "CDATA", "Regimen");
				mmDoc.startElement("", "", "campoAdicional", atts);
				String valor = "Contribuyente Regimen Microempresas";
				mmDoc.characters(valor.toCharArray(), 0, valor.length());
				mmDoc.endElement("", "", "campoAdicional");
				mmDoc.endElement("", "", "infoAdicional");

			}

			if (oi.get_ValueAsBoolean("IsWithholdingAgent")) {

				mmDoc.startElement("", "", "infoAdicional", atts);
				atts.clear();
				atts.addAttribute("", "", "nombre", "CDATA", "Agente de Retencion");
				mmDoc.startElement("", "", "campoAdicional", atts);
				String valor = oi.get_ValueAsString("WithholdingResolution");
				mmDoc.characters(valor.toCharArray(), 0, valor.length());
				mmDoc.endElement("", "", "campoAdicional");
				mmDoc.endElement("", "", "infoAdicional");

			}

			
			mmDoc.endElement("", "", f.get_ValueAsString("XmlPrintLabel"));

			mmDoc.endDocument();

			if (mmDocStream != null) {
				try {
					mmDocStream.close();
				} catch (Exception e2) {
				}
			}

			if (m_sumabaseimponible.compareTo(m_totalbaseimponible) != 0
					&& m_totalbaseimponible.subtract(m_sumabaseimponible).abs().compareTo(LEC_FE_UtilsXml.HALF) > 1) {
				msg = "Error Diferencia Base Impuesto Total: " + m_totalbaseimponible.toString() + " Detalles: "
						+ m_sumabaseimponible.toString();
				return ErrorDocumentno + msg;
			}

			if (m_sumavalorimpuesto.compareTo(m_totalvalorimpuesto) != 0
					&& m_totalvalorimpuesto.subtract(m_sumavalorimpuesto).abs().compareTo(LEC_FE_UtilsXml.HALF) > 1) {
				msg = "Error Diferencia Impuesto Total: " + m_totalvalorimpuesto.toString() + " Detalles: "
						+ m_sumavalorimpuesto.toString();
				return ErrorDocumentno + msg;
			}

//		if (LEC_FE_Utils.breakDialog("Firmando Xml")) return "Cancelado...";	// TODO Temp

			log.warning("@Signing Xml@ -> " + file_name);
			signature.setResource_To_Sign(file_name);
			signature.setOutput_Directory(
					signature.getFolderRaiz() + File.separator + LEC_FE_UtilsXml.folderComprobantesFirmados);
			signature.execute();

			file_name = signature.getFilename(signature, LEC_FE_UtilsXml.folderComprobantesFirmados);

			if (!signature.IsUseContingency) {

//	        if (LEC_FE_Utils.breakDialog("Enviando Comprobante al SRI")) return "Cancelado...";	// TODO Temp

				// Procesar Recepcion SRI
				log.warning("@Sending Xml@ -> " + file_name);
				msg = signature.respuestaRecepcionComprobante(file_name);

				if (msg != null)
					if (!msg.equals("RECIBIDA")) {

						return ErrorDocumentno + msg;
					}
				String invoiceNo = getDocumentNo();
				String invoiceID = String.valueOf(get_ID());
				a.setDescription(invoiceNo);
				a.set_ValueOfColumn("DocumentID", invoiceID);
				a.saveEx();

				// Procesar Autorizacion SRI
				// 04/07/2016 MHG Offline Schema added
				if (!isOfflineSchema) {
					log.warning("@Authorizing Xml@ -> " + file_name);
					try {
						msg = signature.respuestaAutorizacionComprobante(ac, a, m_accesscode);

						if (msg != null) {

							return ErrorDocumentno + msg;
						}
					} catch (Exception ex) {
						// Completar en estos casos, luego usar Boton Reprocesar Autorizacion
						// 70-Clave de acceso en procesamiento
						if (a.getSRI_ErrorCode().getValue().equals("70"))
							// ignore exceptions
							log.warning(msg + ex.getMessage());
						else
							return msg;
					}
					file_name = signature.getFilename(signature, LEC_FE_UtilsXml.folderComprobantesAutorizados);
				} else {
					msg = null;
					file_name = signature.getFilename(signature, LEC_FE_UtilsXml.folderComprobantesEnProceso);
				}
			} else { // emisionContingencia
				// Completar en estos casos, luego usar Boton Procesar Contingencia
				// 170-Clave de contingencia pendiente
				a.setSRI_ErrorCode_ID(LEC_FE_Utils.getErrorCode("170"));
				a.saveEx();

				if (signature.isAttachXml())
					LEC_FE_Utils.attachXmlFile(a.getCtx(), a.get_TrxName(), a.getSRI_Authorization_ID(), file_name);

			}


			//
		} catch (Exception e) {
			msg = "No se pudo crear XML - " + msgStatus + " - " + e.getMessage();

			return ErrorDocumentno + msg;
		} catch (Error e) {
			msg = "No se pudo crear XML - Error en Conexion con el SRI";
			return ErrorDocumentno + msg;
		}

		log.warning("@SRI_FileGenerated@ -> " + file_name);
		
		return msg;

	} // lecfeinvnc_SriExportNotaCreditoXML100

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

} // LEC_FE_MNotaDebito