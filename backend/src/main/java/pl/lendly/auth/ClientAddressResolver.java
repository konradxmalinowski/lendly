package pl.lendly.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import pl.lendly.config.AuthProperties;

import java.net.InetAddress;
import java.util.List;

@Component
public class ClientAddressResolver {
    private final AuthProperties properties;

    public ClientAddressResolver(AuthProperties properties) { this.properties = properties; }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!trusted(peer, properties.getTrustedProxies())) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.contains(",")) return peer;
        String candidate = forwarded.trim();
        if (!isAddressLiteral(candidate)) return peer;
        try {
            InetAddress.getByName(candidate);
            return candidate;
        } catch (Exception ignored) {
            return peer;
        }
    }

    private boolean isAddressLiteral(String candidate) {
        if (candidate.indexOf('%') >= 0 || candidate.startsWith("[") || candidate.endsWith("]")) return false;
        if (candidate.contains(":")) return candidate.matches("[0-9A-Fa-f:.]+");
        String[] octets = candidate.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")) return false;
            try { if (Integer.parseInt(octet) > 255) return false; }
            catch (NumberFormatException exception) { return false; }
        }
        return true;
    }

    private boolean trusted(String address, List<String> ranges) {
        for (String range : ranges) {
            try {
                String[] parts = range.split("/", 2);
                byte[] ip = InetAddress.getByName(address).getAddress();
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                if (ip.length != network.length) continue;
                int prefix = parts.length == 1 ? ip.length * 8 : Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > ip.length * 8) continue;
                boolean match = true;
                for (int bit = 0; bit < prefix; bit++) {
                    int mask = 1 << (7 - bit % 8);
                    if ((ip[bit / 8] & mask) != (network[bit / 8] & mask)) { match = false; break; }
                }
                if (match) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }
}
