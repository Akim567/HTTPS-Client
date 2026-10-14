package netdiag.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Выполняет только DNS-резолвинг имени хоста.
 */
public class DnsResolver {

    public static InetAddress[] resolve(String host)
            throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}