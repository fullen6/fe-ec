/**
 * Copyright 2013 Ministerio de Industria, Energía y Turismo
 *
 * Este fichero es parte de "Componentes de Firma XAdES 1.1.7".
 *
 * Licencia con arreglo a la EUPL, Versión 1.1 o –en cuanto sean aprobadas por la Comisión Europea– versiones posteriores de la EUPL (la Licencia);
 * Solo podrá usarse esta obra si se respeta la Licencia.
 *
 * Puede obtenerse una copia de la Licencia en:
 *
 * http://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Salvo cuando lo exija la legislación aplicable o se acuerde por escrito, el programa distribuido con arreglo a la Licencia se distribuye «TAL CUAL»,
 * SIN GARANTÍAS NI CONDICIONES DE NINGÚN TIPO, ni expresas ni implícitas.
 * Véase la Licencia en el idioma concreto que rige los permisos y limitaciones que establece la Licencia.
 */
package org.globalqss.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MTable;
import org.compiere.util.Env;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import es.mityc.firmaJava.libreria.utilidades.UtilidadTratarNodo;
import es.mityc.firmaJava.libreria.xades.DataToSign;
import es.mityc.firmaJava.libreria.xades.FirmaXML;
import es.mityc.javasign.issues.PassStoreKS;
import es.mityc.javasign.pkstore.CertStoreException;
import es.mityc.javasign.pkstore.IPKStoreManager;
import es.mityc.javasign.pkstore.keystore.KSStore;

/**
 * <p>
 * Clase base que instancian los diferentes comprobantes para realizar firmas
 * XML.
 * </p>
 * 
 */
public abstract class GenericXMLSignature {

	public static String ambienteCertificacion = "1";
	public static String ambienteProduccion = "2";
	
	public static String nombreCertificacion = "PRUEBAS";
	public static String nombreProduccion = "PRODUCCIÓN";
	
	public static String emisionNormal = "1";
	public static String emisionContingencia = "2";
	
	public static String claveAccesoAutomatica = "1";
	public static String claveAccesoContingencia = "2";
	
	public static String folderComprobantesGenerados = "ComprobantesGenerados";
	public static String folderComprobantesFirmados = "ComprobantesFirmados";
	public static String folderComprobantesTransmitidos = "ComprobantesTransmitidos";
	public static String folderComprobantesRechazados = "ComprobantesRechazados";
	public static String folderComprobantesAutorizados = "ComprobantesAutorizados";
	public static String folderComprobantesNoAutorizados = "ComprobantesNoAutorizados";
	
	public static String folderComprobantesEnProceso = "ComprobantesEnProceso";

	public static String recepcionComprobantesQname = "http://ec.gob.sri.ws.recepcion";
	//public static String recepcionComprobantesService = "RecepcionComprobantesService";
	public static String recepcionRecibida = "RECIBIDA";
	public static String recepcionDevuelta = "DEVUELTA";
	
	public static String autorizacionComprobantesQname = "http://ec.gob.sri.ws.autorizacion";
	//public static String autorizacionComprobantesService = "AutorizacionComprobantesService";
	public static String comprobanteAutorizado = "AUTORIZADO";
	public static String comprobanteNoAutorizado = "NO AUTORIZADO";
	public static String comprobanteRechazado = "RECHAZADO";
	public static String mensajeError = "ERROR";
	public static String mensajeInformativo = "INFORMATIVO";
	
	public static String serviceWord = "Service";
	
	private static final String ID_CE_CERTIFICATE_POLICIES = "2.5.29.32";

	/**	Big Decimal 0.5	 */
	static final public BigDecimal HALF = new BigDecimal(0.5);
	
	public int AD_Org_ID;
	
	public int getAD_Org_ID() {
		return AD_Org_ID;
	}

	public void setAD_Org_ID(int aD_Org_ID) {
		AD_Org_ID = aD_Org_ID;
	}

	public boolean isOnTesting;

	public boolean isOnTesting() {
		return isOnTesting;
	}

	public void setOnTesting(boolean isOnTesting) {
		this.isOnTesting = isOnTesting;
	}
	
	public boolean isAttachXml;

	public boolean isAttachXml() {
		return isAttachXml;
	}

	public void setAttachXml(boolean isAttachXml) {
		this.isAttachXml = isAttachXml;
	}
	
	public boolean IsUseContingency;

	public boolean isIsUseContingency() {
		return IsUseContingency;
	}

	public void setIsUseContingency(boolean isUseContingency) {
		IsUseContingency = isUseContingency;
	}

	public String EnvType = "";
	
	
	public String getEnvType() {
		return EnvType;
	}

	public void setEnvType(String envType) {
		EnvType = envType;
	}
	
	public String DeliveredType = "";

	public String getDeliveredType() {
		return DeliveredType;
	}

	public void setDeliveredType(String deliveredType) {
		DeliveredType = deliveredType;
	}

	public String CodeAccessType = "";

	public String getCodeAccessType() {
		return CodeAccessType;
	}

	public void setCodeAccessType(String codeAccessType) {
		CodeAccessType = codeAccessType;
	}

	/** WS URLs			*/
	public String urlWSRecepcionComprobantes = "";
	
	public String getUrlWSRecepcionComprobantes() {
		return urlWSRecepcionComprobantes;
	}

	public void setUrlWSRecepcionComprobantes(String urlWSRecepcionComprobantes) {
		this.urlWSRecepcionComprobantes = urlWSRecepcionComprobantes;
	}

	public String urlWSAutorizacionComprobantes = "";
	
	public String getUrlWSAutorizacionComprobantes() {
		return urlWSAutorizacionComprobantes;
	}

	public void setUrlWSAutorizacionComprobantes(
			String urlWSAutorizacionComprobantes) {
		this.urlWSAutorizacionComprobantes = urlWSAutorizacionComprobantes;
	}
	
	public int SriWSTimeout = 0;

	public int getSriWSTimeout() {
		return SriWSTimeout;
	}

	public void setSriWSTimeout(int sriWSTimeout) {
		SriWSTimeout = sriWSTimeout;
	}

	/** Dir				*/
	public String folderRaiz = "";
	
	public String getFolderRaiz() {
		return folderRaiz;
	}

	public void setFolderRaiz(String folderRaiz) {
		this.folderRaiz = folderRaiz;
	}

	public String XmlEncoding = "UTF-8";
	
	public String getXmlEncoding() {
		return XmlEncoding;
	}

	public void setXmlEncoding(String xmlEncoding) {
		XmlEncoding = xmlEncoding;
	}

	public String Resource_To_Sign = "";
	
	public String getResource_To_Sign() {
		return Resource_To_Sign;
	}

	public void setResource_To_Sign(String resource_To_Sign) {
		Resource_To_Sign = resource_To_Sign;
	}

	/**
     * <p>
     * Almacén PKCS12 con el que se desea realizar la firma
     * </p>
     */
    public String PKCS12_Resource = "";

	public String getPKCS12_Resource() {
		return PKCS12_Resource;
	}

	public void setPKCS12_Resource(String pKCS12_Resource) {
		PKCS12_Resource = pKCS12_Resource;
	}

	/**
     * <p>
     * Constraseña de acceso a la clave privada del usuario
     * </p>
     */
    public String PKCS12_Password = "changeit";
    
	public String getPKCS12_Password() {
		return PKCS12_Password;
	}

	public void setPKCS12_Password(String pKCS12_Password) {
		PKCS12_Password = pKCS12_Password;
	}

	/**
     * <p>
     * Directorio donde se almacenará el resultado de la firma
     * </p>
     */
    String Output_Directory = "/tmp";

    public String getOutput_Directory() {
		return Output_Directory;
	}

	public void setOutput_Directory(String output_Directory) {
		Output_Directory = output_Directory;
	}

	/**
     * <p>
     * Ejecución del ejemplo. La ejecución consistirá en la firma de los datos
     * creados por el método abstracto <code>createDataToSign</code> mediante el
     * certificado declarado en la constante <code>PKCS12_FILE</code>. El
     * resultado del proceso de firma será almacenado en un fichero XML en el
     * directorio correspondiente a la constante <code>OUTPUT_DIRECTORY</code>
     * del usuario bajo el nombre devuelto por el método abstracto
     * <code>getSignFileName</code>
     * </p>
     */
    public void execute() {

        // Obtencion del gestor de claves
        IPKStoreManager storeManager = getPKStoreManager();
        if (storeManager == null) {
            System.err.println("El gestor de claves no se ha obtenido correctamente.");
            throw new AdempiereException("El gestor de claves no se ha obtenido correctamente.");
        }

        // Obtencion del certificado para firmar. Utilizaremos el primer
        // certificado del almacen.
        X509Certificate certificate = getFirstCertificate(storeManager);
        if (certificate == null) {
            System.err.println("No existe ningún certificado para firmar.");
            throw new AdempiereException("No existe ningún certificado para firmar.");
        }

        // Obtención de la clave privada asociada al certificado
        PrivateKey privateKey;
        try {
            privateKey = storeManager.getPrivateKey(certificate);
        } catch (CertStoreException e) {
            System.err.println("Error al acceder al almacén.");
            throw new AdempiereException("Error al acceder al almacén.");
        }

        // Obtención del provider encargado de las labores criptográficas
        Provider provider = storeManager.getProvider(certificate);

        /*
         * Creación del objeto que contiene tanto los datos a firmar como la
         * configuración del tipo de firma
         */
        DataToSign dataToSign = createDataToSign();

        /*
         * Creación del objeto encargado de realizar la firma
         */
        FirmaXML firma = new FirmaXML();

        // Firmamos el documento
        Document docSigned = null;
        String fileSigned = "";
        
        try {
            Object[] res = firma.signFile(certificate, dataToSign, privateKey, provider);
            docSigned = (Document) res[0];
        } catch (Exception ex) {
            System.err.println("Error realizando la firma");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        }

        // Guardamos la firma a un fichero en el path indicado
        fileSigned = getOutput_Directory() + File.separator + getSignatureFileName().substring(getSignatureFileName().lastIndexOf(File.separator) + 1);
        System.out.println("Firma salvada en: " + fileSigned);
        saveDocumentToFile(docSigned, fileSigned);
    }
    
    /**
     * <p>
     * Crea el objeto DataToSign que contiene toda la información de la firma
     * que se desea realizar. Todas las implementaciones deberán proporcionar
     * una implementación de este método
     * </p>
     * 
     * @return El objeto DataToSign que contiene toda la información de la firma
     *         a realizar
     */
    public DataToSign createDataToSign() {
    	DataToSign datatosign = new DataToSign();
    	return datatosign;
    }

    /**
     * <p>
     * Nombre del fichero donde se desea guardar la firma generada. Todas las
     * implementaciones deberán proporcionar este nombre.
     * </p>
     * 
     * @return El nombre donde se desea guardar la firma generada
     */
    public String getSignatureFileName() {
    	if (! getResource_To_Sign().contains("_sig"))
    		return getResource_To_Sign() + "_sig.xml";
    	
    	return getResource_To_Sign();
    }

    /**
     * <p>
     * Escribe el documento a un fichero.
     * </p>
     * 
     * @param document
     *            El documento a imprmir
     * @param pathfile
     *            El path del fichero donde se quiere escribir.
     */
    public void saveDocumentToFile(Document document, String pathfile) {
        try {
            FileOutputStream fos = new FileOutputStream(pathfile);
            UtilidadTratarNodo.saveDocumentToOutputStream(document, fos, true);
        } catch (FileNotFoundException e) {
            System.err.println("Error al salvar el documento");
            e.printStackTrace();
            throw new AdempiereException(e);
        }
    }

    /**
     * <p>
     * Escribe el documento a un fichero. Esta implementacion es insegura ya que
     * dependiendo del gestor de transformadas el contenido podría ser alterado,
     * con lo que el XML escrito no sería correcto desde el punto de vista de
     * validez de la firma.
     * </p>
     * 
     * @param document
     *            El documento a imprmir
     * @param pathfile
     *            El path del fichero donde se quiere escribir.
     */
    public void saveDocumentToFileUnsafeMode(Document document, String pathfile) {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer serializer;
        try {
            serializer = tfactory.newTransformer();

            serializer.transform(new DOMSource(document), new StreamResult(new File(pathfile)));
        } catch (TransformerException e) {
            System.err.println("Error al salvar el documento");
            e.printStackTrace();
            throw new AdempiereException(e);
        }
    }

    /**
     * <p>
     * Devuelve el <code>Document</code> correspondiente al
     * <code>resource</code> pasado como parámetro
     * </p>
     * 
     * @param resource
     *            El recurso que se desea obtener
     * @return El <code>Document</code> asociado al <code>resource</code>
     */
    public Document getDocument(String resource) {
        Document doc = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            doc = dbf.newDocumentBuilder().parse(new FileInputStream(resource));
        } catch (ParserConfigurationException ex) {
            System.err.println("Error al parsear el documento");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        } catch (SAXException ex) {
            System.err.println("Error al parsear el documento");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        } catch (IOException ex) {
            System.err.println("Error al parsear el documento");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        } catch (IllegalArgumentException ex) {
            System.err.println("Error al parsear el documento");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        }
        return doc;
    }

    /**
     * <p>
     * Devuelve el contenido del documento XML
     * correspondiente al <code>resource</code> pasado como parámetro
     * </p> como un <code>String</code>
     * 
     * @param resource
     *            El recurso que se desea obtener
     * @return El contenido del documento XML como un <code>String</code>
     */
    public String getDocumentAsString(String resource) {
        Document doc = getDocument(resource);
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer serializer;
        StringWriter stringWriter = new StringWriter();
        try {
            serializer = tfactory.newTransformer();
            serializer.transform(new DOMSource(doc), new StreamResult(stringWriter));
        } catch (TransformerException e) {
            System.err.println("Error al imprimir el documento");
            e.printStackTrace();
            throw new AdempiereException(e);
        }

        return stringWriter.toString();
    }

    /**
     * <p>
     * Devuelve el gestor de claves que se va a utilizar
     * </p>
     * 
     * @return El gestor de claves que se va a utilizar</p>
     */
    public IPKStoreManager getPKStoreManager() {
        IPKStoreManager storeManager = null;
        InputStream inStream = null;
        try {
        	KeyStore ks = KeyStore.getInstance("PKCS12");
        	// Obtencion del certificado para firmar. Utilizando un archivo
        	if (PKCS12_Resource != null)
        		inStream = new FileInputStream(PKCS12_Resource);
            // Obtencion del certificado para firmar. Utilizando un attachment - AD_Org
        	if (inStream == null) {
        		MAttachment attach =  MAttachment.get(Env.getCtx(), MTable.getTable_ID("AD_Org"), getAD_Org_ID());
        		if (attach != null) {
	        		for (MAttachmentEntry entry : attach.getEntries()) {
		            	if (entry.getName().endsWith("p12") || entry.getName().endsWith("pfx"))
		            		inStream = new FileInputStream(entry.getFile());
	        		}
        		}
            }
            ks.load(inStream, PKCS12_Password.toCharArray());
            storeManager = new KSStore(ks, new PassStoreKS(PKCS12_Password));
        } catch (KeyStoreException ex) {
            System.err.println("No se puede generar KeyStore PKCS12");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        } catch (NoSuchAlgorithmException ex) {
            System.err.println("No se puede generar KeyStore PKCS12");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        } catch (CertificateException ex) {
            System.err.println("No se puede generar KeyStore PKCS12");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        } catch (IOException ex) {
            System.err.println("No se puede generar KeyStore PKCS12");
            ex.printStackTrace();
            throw new AdempiereException(ex);
        }
        
        if (inStream != null) {
			try {
				inStream.close();
			} catch (Exception e2) {}
		}
       
        return storeManager;
    }

    /**
	 * <p>
	 * Recupera el primero de los certificados del almacén que contenga políticas
	 * <b>id-ce-certificatePolicies</b> con ID <b>2.5.29.32</b>.
	 * </p>
	 * 
	 * @param storeManager
	 *            Interfaz de acceso al almacén
	 * @return Primer certificado disponible en el almacén
	 * @throws AdempiereException
	 *             cuando el almacen está vacío o no se encuentran certificados con
	 *             políticas.
	 */
	private X509Certificate getFirstCertificate(final IPKStoreManager storeManager) {
		try {
			List<X509Certificate> certs = storeManager.getSignCertificates();
			if (certs == null || certs.isEmpty()) {
				throw new AdempiereException("La lista de certificados se encuentra vacía.");
			}
			X509Certificate certificate = certs.stream().filter(this::hasCertificatePolicies).findFirst()
					.orElseThrow(() -> new AdempiereException("No se encontró ningún certificado con políticas"));
			return certificate;
		} catch (CertStoreException ex) {
			throw new AdempiereException(ex);
		}
	}

	/**
	 * <p>
	 * Verifica la existencia de políticas en el certificado utilizando el campo
	 * <b>id-ce-certificatePolicies</b> con ID <b>2.5.29.32</b>.
	 * </p>
	 *
	 * @param certificate
	 *            certificado a examinar
	 * @return true si encuentra políticas, false si no encuentra políticas o el
	 *         certificado es nulo
	 */
	private boolean hasCertificatePolicies(X509Certificate certificate) {
		if (certificate != null) {
			byte[] certificatePolicies = certificate.getExtensionValue(ID_CE_CERTIFICATE_POLICIES);
			if (certificatePolicies != null && certificatePolicies.length > 0) {
				return true;
			}
		}

		return false;
	}

}
