package ec.ingeint.erp.process;

import java.util.logging.Level;

import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInvoice;
import org.compiere.model.MMovement;
import org.compiere.model.MSysConfig;
import org.compiere.model.MUser;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.globalqss.model.LEC_FE_MInOut;
import org.globalqss.model.LEC_FE_MInvoice;
import org.globalqss.model.LEC_FE_MNotaCredito;
import org.globalqss.model.LEC_FE_MNotaDebito;
import org.globalqss.model.LEC_FE_MRetencion;
import org.globalqss.model.LEC_FE_Movement;
import org.globalqss.model.X_SRI_Authorization;

import ec.ingeint.erp.model.LEC_FE_MInvoicePL;

public class SRIGeneateOffLineAutForDocument extends SvrProcess {

	String table = "";
	Integer C_Invoice_ID;
	Integer M_InOut_ID;
	Integer M_Movement_ID;
	String msg = "";

	@Override
	protected void prepare() {
		{
			ProcessInfoParameter[] para = getParameter();
			for (int i = 0; i < para.length; i++) {
				String name = para[i].getParameterName();
				if (para[i].getParameter() == null)
					;
				else if (name.equals("ING_Table"))
					table = para[i].getParameterAsString();
				else if (name.equals("C_Invoice_ID"))
					C_Invoice_ID = para[i].getParameterAsInt();
				else if (name.equals("M_InOut_ID"))
					M_InOut_ID = para[i].getParameterAsInt();
				else if (name.equals("M_Movement_ID"))
					M_Movement_ID = para[i].getParameterAsInt();
				else
					log.log(Level.SEVERE, "Unknown Parameter: " + name);
			}

		} // prepare
	}

	/**
	 * Generate Invoices
	 * 
	 * @return info
	 * @throws Exception
	 */

	@Override
	protected String doIt() throws Exception {
		{

			System.setProperty("javax.xml.soap.SAAJMetaFactory", "com.sun.xml.messaging.saaj.soap.SAAJMetaFactoryImpl");

			if ("C_Invoice_ID".equals(table)) {
				MInvoice inv = new MInvoice(getCtx(), C_Invoice_ID, get_TrxName());
				msg = invoiceGenerateXml(inv);
			} else if ("M_InOut_ID".equals(table)) {
				MInOut io = new MInOut(getCtx(), M_InOut_ID, get_TrxName());
				msg = inoutGenerateXml(io);
			} else if ("M_Movement_ID".equals(table)) {
				MMovement mov = new MMovement(getCtx(), M_Movement_ID, get_TrxName());
				msg = movementGenerateXml(mov);
			}
			if (msg != "" && msg != null)
				log.log(Level.SEVERE, msg);

			return msg;
		}

	} // doIt

	private String invoiceGenerateXml(MInvoice inv) {
		int autorization_id = 0;
		if (inv.get_Value("SRI_Authorization_ID") != null) {
			autorization_id = inv.get_ValueAsInt("SRI_Authorization_ID");
		}
		if (autorization_id != 0) {
			X_SRI_Authorization a = new X_SRI_Authorization(inv.getCtx(), autorization_id, inv.get_TrxName());
			if (a != null) {
				if (a.getSRI_AuthorizationDate() != null) {
					// Comprobante autorizado, no se envia de nuevo el xml.
					return null;
				}
			}
		}
		String msg = null;

		MDocType dt = new MDocType(inv.getCtx(), inv.getC_DocTypeTarget_ID(), inv.get_TrxName());

		String shortdoctype = dt.get_ValueAsString("SRI_ShortDocType");

		if (shortdoctype.equals("")) {
			msg = "No existe definicion SRI_ShortDocType: " + dt.toString();
			log.info("Invoice: " + inv.toString() + msg);

			// if (LEC_FE_Utils.breakDialog(msg)) return "Cancelado..."; // Temp

		}

		MUser user = new MUser(inv.getCtx(), inv.getAD_User_ID(), inv.get_TrxName());

		if (!valideUserMail(user) && !shortdoctype.equals("")) {
			msg = "@RequestActionEMailNoTo@";
			return msg;
		}

		msg = null;
		LEC_FE_MInvoice lecfeinv = new LEC_FE_MInvoice(inv.getCtx(), inv.getC_Invoice_ID(), inv.get_TrxName());
		LEC_FE_MInvoicePL lecfeinvpl = new LEC_FE_MInvoicePL(inv.getCtx(), inv.getC_Invoice_ID(), inv.get_TrxName());
		LEC_FE_MNotaCredito lecfeinvnc = new LEC_FE_MNotaCredito(inv.getCtx(), inv.getC_Invoice_ID(),
				inv.get_TrxName());
		LEC_FE_MNotaDebito lecfeinvnd = new LEC_FE_MNotaDebito(inv.getCtx(), inv.getC_Invoice_ID(), inv.get_TrxName());
		LEC_FE_MRetencion lecfeinvret = new LEC_FE_MRetencion(inv.getCtx(), inv.getC_Invoice_ID(), inv.get_TrxName());
		// isSOTrx()
		if (inv.isSOTrx())
			LEC_FE_MRetencion.generateWitholdingNo(inv);

		if (shortdoctype.equals("01")) { // FACTURA
			msg = lecfeinv.lecfeinv_SriExportInvoiceXML100();
		} else if (shortdoctype.equals("03")) { // LIQUIDACION DE COMPRAS
			msg = lecfeinvpl.lecfeinv_SriExportInvoicePLXML100();
		} else if (shortdoctype.equals("04")) { // NOTA DE CR??DITO
			msg = lecfeinvnc.lecfeinvnc_SriExportNotaCreditoXML100();
		} else if (shortdoctype.equals("05")) { // NOTA DE D??BITO
			msg = lecfeinvnd.lecfeinvnd_SriExportNotaDebitoXML100();
			// !isSOTrx()
		} else if (shortdoctype.equals("07")) { // COMPROBANTE DE RETENCI??N
			if (MSysConfig.getBooleanValue("LEC_GenerateWitholdingToComplete", false, lecfeinvret.getAD_Client_ID())) {
				LEC_FE_MRetencion.generateWitholdingNo(inv);
				// Trx tra = Trx.get(inv.get_TrxName(), false);
				// tra.commit();
				msg = lecfeinvret.lecfeinvret_SriExportRetencionXML100();
			}
		} else
			msg = "Formato no habilitado SRI: " + dt.toString() + shortdoctype;

		return msg;
	}

	private String inoutGenerateXml(MInOut inout) {
		int autorization_id = 0;
		if (inout.get_Value("SRI_Authorization_ID") != null) {
			autorization_id = inout.get_ValueAsInt("SRI_Authorization_ID");
		}
		X_SRI_Authorization a = new X_SRI_Authorization(inout.getCtx(), autorization_id, inout.get_TrxName());
		if (a != null) {
			if (a.getSRI_AuthorizationDate() != null) {
				// Comprobante autorizado, no se envia de nuevo el xml.
				return null;
			}
		}
		String msg = null;

		MDocType dt = new MDocType(inout.getCtx(), inout.getC_DocType_ID(), inout.get_TrxName());

		String shortdoctype = dt.get_ValueAsString("SRI_ShortDocType");

		if (shortdoctype.equals("")) {
			msg = "No existe definicion SRI_ShortDocType: " + dt.toString();
			log.info("Invoice: " + inout.toString() + msg);

			// if (LEC_FE_Utils.breakDialog(msg)) return "Cancelado..."; // Temp
		}

		MUser user = new MUser(inout.getCtx(), inout.getAD_User_ID(), inout.get_TrxName());

		if (!valideUserMail(user) && !shortdoctype.equals("")) {
			msg = "@RequestActionEMailNoTo@";
			return msg;
		}

		msg = null;
		LEC_FE_MInOut lecfeinout = new LEC_FE_MInOut(inout.getCtx(), inout.getM_InOut_ID(), inout.get_TrxName());
		// isSOTrx()
		if (shortdoctype.equals("06")) // GU??A DE REMISI??N
			msg = lecfeinout.lecfeinout_SriExportInOutXML100();
		else
			log.warning("Formato no habilitado SRI: " + dt.toString() + shortdoctype);

		return msg;
	}

	private String movementGenerateXml(MMovement movement) {
		int autorization_id = 0;
		if (movement.get_Value("SRI_Authorization_ID") != null) {
			autorization_id = movement.get_ValueAsInt("SRI_Authorization_ID");
		}
		X_SRI_Authorization a = new X_SRI_Authorization(movement.getCtx(), autorization_id, movement.get_TrxName());
		if (a != null) {
			if (a.getSRI_AuthorizationDate() != null) {
				// Comprobante autorizado, no se envia de nuevo el xml.
				return null;
			}
		}
		String msg = null;

		MDocType dt = new MDocType(movement.getCtx(), movement.getC_DocType_ID(), movement.get_TrxName());

		String shortdoctype = dt.get_ValueAsString("SRI_ShortDocType");

		if (shortdoctype.equals("")) {
			msg = "No existe definicion SRI_ShortDocType: " + dt.toString();
			log.info("Invoice: " + movement.toString() + msg);

			// if (LEC_FE_Utils.breakDialog(msg)) return "Cancelado..."; // Temp
		}

		MUser user = new MUser(movement.getCtx(), movement.getAD_User_ID(), movement.get_TrxName());

		if (!valideUserMail(user) && !shortdoctype.equals("")) {
			msg = "@RequestActionEMailNoTo@";
			return msg;
		}

		msg = null;
		LEC_FE_Movement lecfemovement = new LEC_FE_Movement(movement.getCtx(), movement.getM_Movement_ID(),
				movement.get_TrxName());
		// Hardcoded 1000418-SIS UIO COMPANIA RELACIONADA
		// if (shortdoctype.equals("06") && dt.getC_DocType_ID() == 1000418) //
		// GU??A DE REMISI??N
		if (shortdoctype.equals("06"))
			msg = lecfemovement.lecfeMovement_SriExportMovementXML100();
		else
			log.warning("Formato no habilitado SRI: " + dt.toString() + shortdoctype);

		return msg;
	}

	public static boolean valideUserMail(MUser user) {
		if (MSysConfig.getBooleanValue("QSSLEC_FE_EnvioXmlAutorizadoBPEmail", false, user.getAD_Client_ID())) {

			if ((user.get_ID() == 0
					|| user.isNotificationEMail() && (user.getEMail() == null || user.getEMail().length() == 0))) {
				return false;
			}
		}

		return true;

	} // valideUserMail

} // SRIProcessOfflineAuthorizations
