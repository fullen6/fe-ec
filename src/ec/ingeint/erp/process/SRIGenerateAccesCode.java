package ec.ingeint.erp.process;

import java.util.List;
import java.util.logging.Level;

import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInvoice;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.globalqss.model.LEC_FE_MRetencion;
import org.globalqss.model.X_SRI_Authorization;
import org.globalqss.util.LEC_FE_CreateAccessCode;

import com.google.common.base.Strings;

public class SRIGenerateAccesCode extends SvrProcess {

	String TableName = "";
	boolean isPerTable = false;

	/**
	 * Prepare - e.g., get Parameters.
	 */
	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (name.equals("IsPerTable")) {
				isPerTable = para[i].getParameterAsBoolean();
			} else if (name.equals("TableName")) {
				TableName = para[i].getParameterAsString();
			} else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}

	} // prepare

	@Override
	protected String doIt() throws Exception {

		String where = "sri_authorization_id Is Null and issriofflineschema = 'Y' AND IssoTrx='Y' ";

		int total = 0;
		String seq = "";

		// Invoices
		List<MInvoice> invoices = new Query(getCtx(), MInvoice.Table_Name, where, get_TrxName()).list();

		total = invoices.size();

		for (MInvoice invoice : invoices) {

			if (isPerTable) {

				if (!TableName.equals(MInvoice.Table_Name))
					continue;
			}

			if (invoice.getGrandTotal().signum() <= 0 && invoice.getDocumentNo().length()!=17)
				continue;

			log.warning("Por Procesar: " + String.valueOf(total = total - 1));
			log.warning("Factura a procesar: " + invoice.getDocumentNo());
			MDocType dt = new MDocType(getCtx(), invoice.getC_DocTypeTarget_ID(), get_TrxName());
			String DocTypeSri = dt.get_ValueAsString("SRI_ShortDocType");

			X_SRI_Authorization auth = LEC_FE_CreateAccessCode.CreateAccessCode(getCtx(),
					MInvoice.COLUMNNAME_C_Invoice_ID, invoice.getAD_Org_ID(), invoice.getAD_User_ID(),
					invoice.getC_Invoice_ID(), DocTypeSri, invoice.getDateAcct(), invoice.getDocumentNo(),
					get_TrxName());

			if (auth == null)
				continue;

			invoice.set_ValueOfColumn("SRI_Authorization_ID", auth.getSRI_Authorization_ID());
			invoice.saveEx();
		}

		// Shipments
		List<MInOut> inouts = new Query(getCtx(), MInOut.Table_Name, where, get_TrxName()).list();

		for (MInOut inout : inouts) {
			
			if (inout.getDocumentNo().length()!=17)
				continue;			

			if (isPerTable) {

				if (!TableName.equals(MInOut.Table_Name))
					continue;
			}

			log.warning("Por Procesar: " + String.valueOf(total = total - 1));
			log.warning("Entrega a procesar: " + inout.getDocumentNo());
			MDocType dt = new MDocType(getCtx(), inout.getC_DocType_ID(), get_TrxName());
			String DocTypeSri = dt.get_ValueAsString("SRI_ShortDocType");

			if (Strings.isNullOrEmpty(DocTypeSri))
				continue;

			X_SRI_Authorization auth = LEC_FE_CreateAccessCode.CreateAccessCode(getCtx(), MInOut.COLUMNNAME_M_InOut_ID,
					inout.getAD_Org_ID(), inout.getCreatedBy(), inout.getM_InOut_ID(), DocTypeSri,
					inout.getMovementDate(), inout.getDocumentNo(), get_TrxName());

			if (auth == null)
				continue;

			inout.set_ValueOfColumn("SRI_Authorization_ID", auth.getSRI_Authorization_ID());
			inout.saveEx();
		}

		// Withholding
		where = "sri_authorization_id Is Null and issriofflineschema = 'Y' AND IssoTrx= 'N' ";

		invoices = new Query(getCtx(), MInvoice.Table_Name, where, get_TrxName()).list();

		total = invoices.size();

		for (MInvoice invoice : invoices) {

			if (isPerTable) {

				if (!TableName.equals(MInvoice.Table_Name))
					continue;				
			}

			if (invoice.getGrandTotal().signum() <= 0)
				continue;

			log.warning("Por Procesar: " + String.valueOf(total = total - 1));
			log.warning("Retenci??n a procesar: " + invoice.getDocumentNo());

			MDocType dt = MDocType.get(invoice.getCtx(), invoice.getC_DocTypeTarget_ID());
			String DocTypeSri = dt.get_ValueAsString("SRI_ShortDocType");

			int count = DB.getSQLValue(invoice.get_TrxName(),
					"SELECT COUNT(*) " + "FROM LCO_InvoiceWithholding WHERE C_Invoice_ID = ? ",
					invoice.getC_Invoice_ID());

			if (DocTypeSri != null && !DocTypeSri.isEmpty() && count > 0) {

				if (!invoice.get_ValueAsBoolean("IsGenerated"))
					seq = LEC_FE_MRetencion.generateWitholdingNo(invoice);
				else
					seq = DB.getSQLValueString(invoice.get_TrxName(),
							"SELECT DocumentNo FROM LCO_InvoiceWithholding WHERE C_Invoice_ID = ? ",
							invoice.getC_Invoice_ID());
				
				if (seq.length()!=17)
					continue;

				X_SRI_Authorization auth = LEC_FE_CreateAccessCode.CreateAccessCode(getCtx(),
						MInvoice.COLUMNNAME_C_Invoice_ID, invoice.getAD_Org_ID(), invoice.getAD_User_ID(),
						invoice.getC_Invoice_ID(), "07", invoice.getDateAcct(), seq, get_TrxName());

				if (auth == null)
					continue;

				invoice.set_ValueOfColumn("SRI_Authorization_ID", auth.getSRI_Authorization_ID());
				invoice.saveEx();

			}
			if (DocTypeSri.equals("03")) {

				X_SRI_Authorization auth = LEC_FE_CreateAccessCode.CreateAccessCode(getCtx(),
						MInvoice.COLUMNNAME_C_Invoice_ID, invoice.getAD_Org_ID(), invoice.getAD_User_ID(),
						invoice.getC_Invoice_ID(), DocTypeSri, invoice.getDateAcct(), invoice.getDocumentNo(),
						get_TrxName());

				invoice.set_ValueOfColumn("SRI_AuthorizationPL_ID", auth.getSRI_Authorization_ID());
				invoice.saveEx();

			}
		}

		return null;

	}

}
