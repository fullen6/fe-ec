package ve.net.dcs.component;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;
import org.globalqss.process.SRIContingencyGenerate;
import org.globalqss.process.SRIEmailAuthorization;
import org.globalqss.process.SRIGenerateWithholding;
import org.globalqss.process.SRIProcessBatchInOuts;
import org.globalqss.process.SRIProcessBatchMovements;
import org.globalqss.process.SRIProcessBatchSalesInvoices;
import org.globalqss.process.SRIProcessBatchSalesOrders;
import org.globalqss.process.SRIProcessBatchWithholdings;
import org.globalqss.process.SRIReprocessAuthorization;

import ec.ingeint.erp.process.LEC_InvoiceGenerate;
import ec.ingeint.erp.process.SRIGeneateOffLineAutForDocument;
import ec.ingeint.erp.process.SRIGenerateAccesCode;
import ec.ingeint.erp.process.SendAuthorizationSRI;
import ec.ingeint.erp.process.GenerateOfflineAuthorizations;
import ec.ingeint.erp.process.ProcessOfflineAuthorizations;

public class LEC_FE_ProcessFactory implements IProcessFactory {

	@Override
	public ProcessCall newProcessInstance(String className) {
		ProcessCall process = null;
		if ("org.globalqss.process.SRIContingencyGenerate".equals(className)) {
			try {
				process = SRIContingencyGenerate.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIEmailAuthorization".equals(className)) {
			try {
				process = SRIEmailAuthorization.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIReprocessAuthorization".equals(className)) {
			try {
				process = SRIReprocessAuthorization.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIGenerateWithholding".equals(className)) {
			try {
				process = SRIGenerateWithholding.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIProcessBatchWithholdings".equals(className)) {
			try {
				process = SRIProcessBatchWithholdings.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIProcessBatchSalesOrders".equals(className)) {
			try {
				process = SRIProcessBatchSalesOrders.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIProcessBatchSalesInvoices".equals(className)) {
			try {
				process = SRIProcessBatchSalesInvoices.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIProcessBatchMovements".equals(className)) {
			try {
				process = SRIProcessBatchMovements.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if ("org.globalqss.process.SRIProcessBatchInOuts".equals(className)) {
			try {
				process = SRIProcessBatchInOuts.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if (LEC_InvoiceGenerate.class.getCanonicalName().equals(className)) {
			try {
				process = LEC_InvoiceGenerate.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if (GenerateOfflineAuthorizations.class.getCanonicalName().equals(className)) {
			try {
				process = GenerateOfflineAuthorizations.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if (ProcessOfflineAuthorizations.class.getCanonicalName().equals(className)) {
			try {
				process = ProcessOfflineAuthorizations.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if (SRIGeneateOffLineAutForDocument.class.getCanonicalName().equals(className)) {
			try {
				process = SRIGeneateOffLineAutForDocument.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if (SRIGenerateAccesCode.class.getCanonicalName().equals(className)) {
			try {
				process = SRIGenerateAccesCode.class.getConstructor().newInstance();
			} catch (Exception e) {
			}
		} else if (SendAuthorizationSRI.class.getCanonicalName().equals(className)) {
			try {
				process = SendAuthorizationSRI.class.getConstructor().newInstance();
			} catch (Exception e) {

			}
		}

		return process;
	}
}
