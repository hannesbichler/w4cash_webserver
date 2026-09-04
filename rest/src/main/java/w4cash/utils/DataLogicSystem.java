package w4cash.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.openbravo.format.Formats;
import com.openbravo.pos.forms.AppLocal;

public class DataLogicSystem {

    private SentenceFind m_resourcebytes;
    private Map<String, byte[]> resourcescache;
    private Map<String, String> resourceAsXML = new HashMap<>();

    public final void resetResourcesCache() {
        if (resourcescache == null)
            resourcescache = new HashMap<String, byte[]>();
        else
            resourcescache.clear();

        resourceAsXML.clear();
    }

    private final byte[] getResource(String name) {

        byte[] resource;
        resource = resourcescache.get(name);

        if (resource == null) {
            // Primero trato de obtenerlo de la tabla de recursos
            int retry = 3;
            do {
                try {
                    resource = (byte[]) m_resourcebytes.find(name);
                    resourcescache.put(name, resource);
                } catch (Exception e) {
                    resource = null;
                }
                retry--;
            } while (retry > 0 && resource == null);
        }

        return resource;
    }

    public final String getResourceAsXML(String sName) {
        if (!resourceAsXML.containsKey(sName)) {
            String xmlResource = Formats.BYTEA.formatValue(getResource(sName));
            resourceAsXML.put(sName, xmlResource);
        }

        return resourceAsXML.get(sName);
    }

    public final void setResourceAsProperties(String sName, Properties p) {
        if (p == null) {
            // setResource(sName, 0, null); // texto
        } else {
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                p.storeToXML(o, AppLocal.APP_NAME, "UTF8");
                // setResource(sName, 0, o.toByteArray()); // El texto de las
                // propiedades
            } catch (IOException e) { // no deberia pasar nunca
            }
        }
    }

    public final Properties getResourceAsProperties(String sName) {

        Properties p = new Properties();
        try {
            byte[] img = getResourceAsBinary(sName);
            if (img != null) {
                p.loadFromXML(new ByteArrayInputStream(img));
            }
        } catch (IOException e) {
        }
        return p;
    }

    public final void setResourceAsBinary(String sName, byte[] data) {
        // setResource(sName, 2, data);
    }

    public final byte[] getResourceAsBinary(String sName) {
        return getResource(sName);
    }
}
