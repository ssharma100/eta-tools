package com.oracle.toa;

import it.svario.xpathapi.jaxp.XPathAPI;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathException;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ProxyServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServlet.class.getName());
    private static final String DEFAULT_PROP_FILE = "ofsc.properties";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_VALUE = "text/xml;charset=UTF-8";
    public static final String SOAP_ACTION = "SOAPAction";
    public static final String SOAP_ACTION_VALUE = "InboundInterfaceService/inbound_interface";

    public static String username = "";
    public static String password = "";
    public static String companyName = "";
    public static String instanceUrl = "";
    private static TimeZone tz;
    private static DateFormat df;
    private static MessageDigest md;
    private static List<String> debugInfo = new ArrayList<String>();

    private static Configuration configuration;


    static {
        tz = TimeZone.getTimeZone("UTC");
        df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);

        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private static String Post(String companyUrl, String postData) throws IOException {

        String uri = "https://" + companyUrl + "/soap/inbound/";

        HttpPost httpPost = new HttpPost(uri);


        CloseableHttpClient httpclient = HttpClients.createDefault();


        HttpEntity entity = new StringEntity(postData);

        httpPost.setEntity(entity);

        httpPost.setHeader(CONTENT_TYPE, CONTENT_TYPE_VALUE);
        httpPost.setHeader(SOAP_ACTION, SOAP_ACTION_VALUE);

        // Execute HTTP Post Request
        CloseableHttpResponse response = httpclient.execute(httpPost);
        String responseBody = EntityUtils.toString(response.getEntity());

        return responseBody;
    }

    public void init() {

        LOGGER.warn("Starting Servlet: Load Config From: " + DEFAULT_PROP_FILE);
        Configuration configuration = null;
        try {
            configuration = new PropertiesConfiguration(DEFAULT_PROP_FILE);
        } catch (ConfigurationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        username = configuration.getString("USERNAME");
        password = configuration.getString("PASSWORD");
        companyName = configuration.getString("COMPANY_NAME");
        instanceUrl = configuration.getString("INSTANCE_URL");
    }

    public static String getBody(HttpServletRequest request) throws IOException {

        String body = null;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException ex) {
            throw ex;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }

        body = stringBuilder.toString();
        return body;
    }

    public static String GenerateResponseBody(String msgID, String description) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "xmlns:urn=\"urn:toatech:agent\">\n" +
                " <soapenv:Header/>\n" +
                " <soapenv:Body>\n" +
                " <urn:send_message_response>\n" +
                " <urn:message_response>\n" +
                " <urn:message_id>");

        stringBuilder.append(msgID);

        stringBuilder.append("</urn:message_id>\n" +
                " <urn:status>sent</urn:status>\n" +
                " <urn:description>");

        stringBuilder.append(description);

        stringBuilder.append("</urn:description>\n" +
                " </urn:message_response>\n" +
                " </urn:send_message_response>\n" +
                " </soapenv:Body>\n" +
                "</soapenv:Envelope>");

        return stringBuilder.toString();
    }

    public static void BuildUserNode(StringBuilder stringBuilder, String login, String company, String password) {
        stringBuilder.append("<user>");


        String nowAsISO = df.format(new Date());

        AddNodeWithValue(stringBuilder, "now", nowAsISO);
        AddNodeWithValue(stringBuilder, "login", login);
        AddNodeWithValue(stringBuilder, "company", company);

        String hashedPassword = GetMD5Hash(password);
        String authString = GetMD5Hash(nowAsISO + hashedPassword);

        AddNodeWithValue(stringBuilder, "auth_string", authString);
        stringBuilder.append("</user>");
    }

    public static void AddNodeWithValue(StringBuilder stringBuilder, String node, String value) {
        stringBuilder.append("<");
        stringBuilder.append(node);
        stringBuilder.append(">");
        stringBuilder.append(value);
        stringBuilder.append("</");
        stringBuilder.append(node);
        stringBuilder.append(">");
    }

    public static String GetMD5Hash(String input) {

        if (md == null) {
            return "";
        }

        md.update(input.getBytes());
        byte[] digest = md.digest();

        StringBuilder sb = new StringBuilder(33);

        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }

        return sb.toString();
    }

    public static void OpeningHeader(StringBuilder stringBuilder) {
        stringBuilder.append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:urn=\"urn:toatech:InboundInterface:1.0\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <urn:inbound_interface_request>\n");
    }

    public static void ClosingHeader(StringBuilder stringBuilder) {
        stringBuilder.append("</urn:inbound_interface_request>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {

            //Get the request body.
            String requestBody = getBody(request);

            //Extract what I need from the incoming request

            InputSource source = new InputSource(new StringReader(requestBody));

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(source);

            String msgId = XPathAPI.selectSingleNodeAsString(document, "Envelope//message_id");
            String body = XPathAPI.selectSingleNodeAsString(document, "Envelope//body");

            String redirectPayload = XPathAPI.selectSingleNodeAsString(document, "Envelope//login");

            String username = ProxyServlet.username;
            String companyName = ProxyServlet.companyName;
            String password = ProxyServlet.password;
            String instanceUrl = ProxyServlet.instanceUrl;
            boolean debug = false;


            if(redirectPayload != null && !redirectPayload.trim().equals("")){
                String[] info = redirectPayload.split("\\|");
                if(info.length < 4) {
                    pw.write("Incorrect format for username");
                    throw new ConfigurationException();
                }
                username = info[0];
                password = info[1];
                instanceUrl = info[2];
                companyName = info[3];

                //We want to debug.
                if(info.length == 5)
                    debug = true;
            }

            String unescapedBody = StringEscapeUtils.unescapeXml(body);

            //Send an inbound request to OFSC

            StringBuilder inboundRequestBody = new StringBuilder();

            OpeningHeader(inboundRequestBody);

            BuildUserNode(inboundRequestBody, username, companyName, password);

            inboundRequestBody.append(unescapedBody);

            ClosingHeader(inboundRequestBody);

            String reply = Post(instanceUrl, inboundRequestBody.toString());

            String replyDescription;

            if(debug){
                String debugMsg = "Message processed: " + df.format(new Date());
                debugMsg  += "<br><br>Received the following msg from OFSC Outbound:<br>" + LZString.compressToBase64(requestBody);
                debugMsg  += "<br><br>Sent the following msg to OFSC Inbound:<br>" + LZString.compressToBase64(inboundRequestBody.toString());
                debugMsg  += "<br><br>Received the following msg from OFSC Inbound:<br>" + LZString.compressToBase64(reply);

                debugInfo.add(debugMsg);

                while(debugInfo.size() > 10){
                    debugInfo.remove(0);
                }

                replyDescription = "Debug info saved to JCS";

            }else{
                replyDescription = "Everything is fine";
            }

            //Write response back to OFSC
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter writer = response.getWriter();
            writer.write(GenerateResponseBody(msgId, replyDescription));
            writer.flush();
            return;
        } catch (ConfigurationException | ParserConfigurationException | SAXException | XPathException e) {
            e.printStackTrace(pw);
        }
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().write(sw.toString());
        response.getWriter().flush();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = response.getWriter();
        StringBuilder documentBody = new StringBuilder();

        OpenTag(documentBody, "html");

        documentBody.append("<head>\n" +
                "<style>\n" +
                "table, th, td {\n" +
                "    border: 1px solid black;\n" +
                "}\n" +
                "</style>\n" +
                "</head>");

        OpenTag(documentBody, "body");
        documentBody.append("<h1>Last 10 messages</h1>\r\n");
        OpenTag(documentBody, "table cellpadding=\"10\" style=\"word-break: break-all;\"");

        for(String debugLog : debugInfo){
            OpenTag(documentBody, "tr");
            OpenTag(documentBody, "td");

            documentBody.append(debugLog);

            CloseTag(documentBody, "td");
            CloseTag(documentBody, "tr");
        }

        CloseTag(documentBody, "table");
        CloseTag(documentBody, "body");
        CloseTag(documentBody, "html");

        writer.write(documentBody.toString());

        writer.flush();
    }

    private static void OpenTag(StringBuilder builder, String tag){
        builder.append("<");
        builder.append(tag);
        builder.append(">\r\n");
    }

    private static void CloseTag(StringBuilder builder, String tag){
        builder.append("</");
        builder.append(tag);
        builder.append(">\r\n");
    }
}
