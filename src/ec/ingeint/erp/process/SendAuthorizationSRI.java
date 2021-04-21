package ec.ingeint.erp.process;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.List;

import org.compiere.model.MInvoice;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.globalqss.model.LEC_FE_MInOut;
import org.globalqss.model.LEC_FE_MInvoice;
import org.globalqss.model.LEC_FE_MNotaCredito;
import org.globalqss.model.LEC_FE_MNotaDebito;
import org.globalqss.model.LEC_FE_MRetencion;
import org.globalqss.model.LEC_FE_Movement;
import org.globalqss.model.X_SRI_Authorization;

import ec.ingeint.erp.model.LEC_FE_MInvoicePL;

public class SendAuthorizationSRI extends SvrProcess {

	private static CLogger log = CLogger.getCLogger(SendAuthorizationSRI.class);
	Timestamp DateAcct = null;
	Timestamp DateAcctTo = null;

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (name.equals(MInvoice.COLUMNNAME_DateAcct)) {
				DateAcct = para[i].getParameterAsTimestamp();
				DateAcctTo = para[i].getParameter_ToAsTimestamp();
			} else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}

	} // prepare

	@Override
	protected String doIt() throws Exception {
		
		System.setProperty("javax.xml.soap.SAAJMetaFactory", "com.sun.xml.messaging.saaj.soap.SAAJMetaFactoryImpl");

		String where = "istosend = 'Y' ";
		String msg = "";
		int count = 0;
		List<X_SRI_Authorization> authorizations = new Query(getCtx(), X_SRI_Authorization.Table_Name, where,
				get_TrxName()).list();

		for (X_SRI_Authorization auth : authorizations) {

			int record_id = auth.get_ValueAsInt("documentid");
			

			if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_Invoice)) {
				LEC_FE_MInvoice lecfeinv = new LEC_FE_MInvoice(getCtx(), record_id, get_TrxName());
				msg = lecfeinv.lecfeinv_SriExportInvoiceXML100();
			} else if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_PurchaseLiquidation)) {
				LEC_FE_MInvoicePL lecfeinvpl = new LEC_FE_MInvoicePL(getCtx(), record_id, get_TrxName());
				msg = lecfeinvpl.lecfeinv_SriExportInvoicePLXML100();
			} else if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_CreditMemo)) {
				LEC_FE_MNotaCredito lecfeinvnc = new LEC_FE_MNotaCredito(getCtx(), record_id, get_TrxName());
				msg = lecfeinvnc.lecfeinvnc_SriExportNotaCreditoXML100();
			} else if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_DebitMemo)) {
				LEC_FE_MNotaDebito lecfeinvnd = new LEC_FE_MNotaDebito(getCtx(), record_id, get_TrxName());
				msg = lecfeinvnd.lecfeinvnd_SriExportNotaDebitoXML100();
			} else if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_Shipment) && auth.getM_InOut_ID() > 0) {
				LEC_FE_MInOut lecfeinout = new LEC_FE_MInOut(getCtx(), record_id, get_TrxName());
				msg = lecfeinout.lecfeinout_SriExportInOutXML100();
			} else if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_Shipment) && auth.getM_Movement_ID() > 0) { 
				LEC_FE_Movement lecfemov = new LEC_FE_Movement(getCtx(), record_id, get_TrxName());
				msg = lecfemov.lecfeMovement_SriExportMovementXML100();				
			} else if (auth.getSRI_ShortDocType().equals(X_SRI_Authorization.SRI_SHORTDOCTYPE_Withholding)) {
				LEC_FE_MRetencion lecfeinvret = new LEC_FE_MRetencion(getCtx(), record_id, get_TrxName());
				if (new BigDecimal(lecfeinvret.get_ValueAsString("WithholdingAmt")).signum() > 0) {
					LEC_FE_MRetencion.generateWitholdingNo(lecfeinvret);					
				}
				msg = lecfeinvret.lecfeinvret_SriExportRetencionXML100();
			}
			
			if (msg == null)
				count++;	
		}

		return "Procesados: "+count;
	}
}