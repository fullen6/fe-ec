package ec.ingeint.erp.process;

import java.util.List;
import java.util.logging.Level;

import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.globalqss.model.LEC_FE_MRetencion;
import org.globalqss.model.X_SRI_Authorization;
import org.globalqss.util.LEC_FE_CreateAccessCode;

public class SRIGenerateAccesCode extends SvrProcess {

	/**
	 * Prepare - e.g., get Parameters.
	 */
	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}

	} // prepare

	@Override
	protected String doIt() throws Exception {

		String where = "sri_authorization_id Is Null and issriofflineschema = 'Y' AND IssoTrx='Y' ";

		int total = 0;
		String seq = "";

		List<MInvoice> invoices = new Query(getCtx(), MInvoice.Table_Name, where, get_TrxName()).list();

		total = invoices.size();

		for (MInvoice invoice : invoices) {
			
			if (invoice.getGrandTotal().signum() <= 0)
				continue;

			log.warning("Por Procesar: " + String.valueOf(total = total - 1));
			log.warning("Factura a procesar: " + invoice.getDocumentNo());
			MDocType dt = new MDocType(getCtx(), invoice.getC_DocTypeTarget_ID(), get_TrxName());
			String DocTypeSri = dt.get_ValueAsString("SRI_ShortDocType");
			

			X_SRI_Authorization auth = LEC_FE_CreateAccessCode.CreateAccessCode(getCtx(),
					MInvoice.COLUMNNAME_C_Invoice_ID, invoice.getAD_Org_ID(), invoice.getAD_User_ID(),
					invoice.getC_Invoice_ID(), DocTypeSri, invoice.getDateAcct(),
					invoice.getDocumentNo(), get_TrxName());

			invoice.set_ValueOfColumn("SRI_Authorization_ID", auth.getSRI_Authorization_ID());
			invoice.saveEx();

		}

		where = "sri_authorization_id Is Null and issriofflineschema = 'Y' AND IssoTrx= 'N' ";

		invoices = new Query(getCtx(), MInvoice.Table_Name, where, get_TrxName()).list();

		total = invoices.size();

		for (MInvoice invoice : invoices) {			

			if (invoice.getGrandTotal().signum() <= 0)
				continue;

			log.warning("Por Procesar: " + String.valueOf(total = total - 1));
			log.warning("Factura a procesar: " + invoice.getDocumentNo());

			MDocType dt = MDocType.get(invoice.getCtx(), invoice.getC_DocTypeTarget_ID());
			
			int count = DB.getSQLValue(invoice.get_TrxName(),
					"SELECT COUNT(*) " + "FROM LCO_InvoiceWithholding WHERE C_Invoice_ID = ? ",
					invoice.getC_Invoice_ID());


			if (dt.get_ValueAsString("SRI_ShortDocType") != null && !dt.get_ValueAsString("SRI_ShortDocType").isEmpty()
					&& count > 0 ) {
				
				if (!invoice.get_ValueAsBoolean("IsGenerated"))
					seq = LEC_FE_MRetencion.generateWitholdingNo(invoice);
				else
					seq = DB.getSQLValueString(invoice.get_TrxName(), "SELECT DocumentNo FROM LCO_InvoiceWithholding WHERE C_Invoice_ID = ? ", invoice.getC_Invoice_ID());

				X_SRI_Authorization auth = LEC_FE_CreateAccessCode.CreateAccessCode(getCtx(),
						MInvoice.COLUMNNAME_C_Invoice_ID, invoice.getAD_Org_ID(), invoice.getAD_User_ID(),
						invoice.getC_Invoice_ID(), dt.get_ValueAsString("SRI_ShortDocType"), invoice.getDateAcct(), seq,
						get_TrxName());

				invoice.set_ValueOfColumn("SRI_Authorization_ID", auth.getSRI_Authorization_ID());
				invoice.saveEx();

			}
		}

		return null;

	}

}
