package org.globalqss.util;

import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MSysConfig;
import org.compiere.util.Env;
import org.globalqss.model.X_SRI_AccessCode;
import org.globalqss.model.X_SRI_Authorization;

public class LEC_FE_CreateAccessCode {

	private static boolean isOfflineSchema = false;

	/**
	 * 
	 * @param ctx
	 * @param Column_ID
	 * @param AD_Org_ID
	 * @param AD_User_ID
	 * @param ID
	 * @param C_DocType
	 * @param DateDoc
	 * @param DocumentNo
	 * @param trxName
	 * @return
	 */
	public static X_SRI_Authorization CreateAccessCode(Properties ctx, String Column_ID, int AD_Org_ID, int AD_User_ID,
			int ID, String codeDoc, Timestamp DateDoc, String DocumentNo, String trxName) {

		MOrgInfo oi = MOrgInfo.get(ctx, AD_Org_ID, trxName);
		String m_coddoc = codeDoc;
		
		if (m_coddoc.isEmpty())
			return null;

		// 04/07/2016 MHG Offline Schema added
		isOfflineSchema = MSysConfig.getBooleanValue("QSSLEC_FE_OfflineSchema", false,
				Env.getAD_Client_ID(Env.getCtx()));

		String ErrorDocumentno = "Error en Factura No " + DocumentNo + " ";
		X_SRI_Authorization a = null;
		String msg = null;
		LEC_FE_UtilsXml signature = new LEC_FE_UtilsXml();

		signature.setAD_Org_ID(AD_Org_ID);
		signature.setPKCS12_Resource(
				MSysConfig.getValue("QSSLEC_FE_RutaCertificadoDigital", null, oi.getAD_Client_ID()));
		signature.setPKCS12_Password(
				MSysConfig.getValue(""
						+ "", null, oi.getAD_Client_ID()));

		if (signature.getFolderRaiz() == null)
			throw new AdempiereException("No existe parametro para Ruta Generacion Xml");

		X_SRI_AccessCode ac = null;
		ac = new X_SRI_AccessCode(ctx, 0, trxName);
		ac.setAD_Org_ID(AD_Org_ID);
		ac.setOldValue(null);
		ac.setEnvType(signature.getEnvType());
		ac.setCodeAccessType(signature.getCodeAccessType());
		ac.setSRI_ShortDocType(m_coddoc);
		ac.setIsUsed(true);

		// Access Code
		String m_accesscode = LEC_FE_Utils.getAccessCode(DateDoc, m_coddoc, oi.getTaxID(),
				LEC_FE_Utils.getOrgCode(LEC_FE_Utils.formatDocNo(DocumentNo, m_coddoc)),
				LEC_FE_Utils.getStoreCode(LEC_FE_Utils.formatDocNo(DocumentNo, m_coddoc)), DocumentNo,
				oi.get_ValueAsString("SRI_DocumentCode"), signature.getDeliveredType(), ac);

		if (signature.getCodeAccessType().equals(LEC_FE_UtilsXml.claveAccesoAutomatica))
			ac.setValue(m_accesscode);

		if (!ac.save(trxName)) {
			throw new AdempiereException("@SaveError@ No se pudo grabar SRI Access Code");

		}

		// New Authorization

		a = new X_SRI_Authorization(ctx, 0, trxName);
		a.setAD_Org_ID(AD_Org_ID);
		a.setSRI_ShortDocType(m_coddoc);
		a.setValue(m_accesscode);
		a.setSRI_AccessCode_ID(ac.get_ID());
		a.setSRI_ErrorCode_ID(0);
		a.setAD_UserMail_ID(AD_User_ID);
		a.set_ValueOfColumn("isSRIOfflineSchema", isOfflineSchema);
		a.set_ValueOfColumn(Column_ID, ID);
		a.setDescription(DocumentNo);
		a.set_ValueOfColumn("DocumentID", ID);
		a.set_ValueOfColumn("IsToSend", true);

		if (!a.save(trxName)) {
			msg = "@SaveError@ No se pudo crear la autorizacion ";
		} else {

			return a;
		}
		return a;

	}

}
