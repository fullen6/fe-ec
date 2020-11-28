package org.globalqss.util;

import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MSysConfig;
import org.compiere.util.Env;
import org.globalqss.model.X_SRI_AccessCode;
import org.globalqss.model.X_SRI_Authorization;

public class LEC_FE_CreateAccessCode {

	private static boolean isOfflineSchema = false;

	public static String CreateAccessCode(MInvoice invoice) {

		// 04/07/2016 MHG Offline Schema added
		isOfflineSchema = MSysConfig.getBooleanValue("QSSLEC_FE_OfflineSchema", false,
				Env.getAD_Client_ID(Env.getCtx()));

		String ErrorDocumentno = "Error en Factura No " + invoice.getDocumentNo() + " ";
		X_SRI_Authorization a = null;
		String msg = null;
		LEC_FE_UtilsXml signature = new LEC_FE_UtilsXml();

		signature.setAD_Org_ID(invoice.getAD_Org_ID());
		signature.setPKCS12_Resource(MSysConfig.getValue("QSSLEC_FE_RutaCertificadoDigital", null,
				invoice.getAD_Client_ID(), invoice.getAD_Org_ID()));
		signature.setPKCS12_Password(MSysConfig.getValue("QSSLEC_FE_ClaveCertificadoDigital", null,
				invoice.getAD_Client_ID(), invoice.getAD_Org_ID()));

		if (signature.getFolderRaiz() == null)
			return ErrorDocumentno + "No existe parametro para Ruta Generacion Xml";

		MOrgInfo oi = MOrgInfo.get(invoice.getCtx(), invoice.getAD_Org_ID(), invoice.get_TrxName());
		MDocType dt = MDocType.get(invoice.getCtx(), invoice.getC_DocTypeTarget_ID());
		String m_coddoc = dt.get_ValueAsString("SRI_ShortDocType");

		X_SRI_AccessCode ac = null;
		ac = new X_SRI_AccessCode(invoice.getCtx(), 0, invoice.get_TrxName());
		ac.setAD_Org_ID(invoice.getAD_Org_ID());
		ac.setOldValue(null);
		ac.setEnvType(signature.getEnvType());
		ac.setCodeAccessType(signature.getCodeAccessType());
		ac.setSRI_ShortDocType(m_coddoc);
		ac.setIsUsed(true);

		// Access Code
		String m_accesscode = LEC_FE_Utils.getAccessCode(invoice.getDateInvoiced(), m_coddoc,
				invoice.getC_BPartner().getTaxID(),
				LEC_FE_Utils.getOrgCode(LEC_FE_Utils.formatDocNo(invoice.getDocumentNo(), m_coddoc)),
				LEC_FE_Utils.getStoreCode(LEC_FE_Utils.formatDocNo(invoice.getDocumentNo(), m_coddoc)),
				invoice.getDocumentNo(), oi.get_ValueAsString("SRI_DocumentCode"), signature.getDeliveredType(), ac);

		if (signature.getCodeAccessType().equals(LEC_FE_UtilsXml.claveAccesoAutomatica))
			ac.setValue(m_accesscode);

		if (!ac.save()) {
			msg = "@SaveError@ No se pudo grabar SRI Access Code";
			return ErrorDocumentno + msg;
		}

		// New Authorization

		a = new X_SRI_Authorization(invoice.getCtx(), 0, invoice.get_TrxName());
		a.setAD_Org_ID(invoice.getAD_Org_ID());
		a.setSRI_ShortDocType(m_coddoc);
		a.setValue(m_accesscode);
		a.setSRI_AccessCode_ID(ac.get_ID());
		a.setSRI_ErrorCode_ID(0);
		a.setAD_UserMail_ID(invoice.getAD_User_ID());
		a.set_ValueOfColumn("isSRIOfflineSchema", isOfflineSchema);
		a.set_ValueOfColumn("C_Invoice_ID", invoice.get_ID());
		a.setDescription(invoice.getDocumentNo());
		a.set_ValueOfColumn("DocumentID", invoice.getC_Invoice_ID());
		a.set_ValueOfColumn("IsToSend", true);

		if (!a.save()) {
			msg = "@SaveError@ No se pudo crear la autorizacion ";
		} else {
			invoice.set_ValueOfColumn("SRI_Authorization_ID", a.getSRI_Authorization_ID());
			invoice.saveEx();
			return "Created";

		}
		return m_accesscode;

	}

}
