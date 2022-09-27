package edu.ut.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.ut.cs.sdn.vnet.Device;
import edu.ut.cs.sdn.vnet.DumpFile;
import edu.ut.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {

	private HashMap<CachableMACAddress, Iface> switchTable;

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile) {
		super(host, logfile);
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * 
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface     the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface) {
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		CachableEntry entry = switchTable.get(etherPacket.getDestinationMAC());
		if (entry != null && !entry.isExpired()) {
			entry.resetExpirationTime();
			if (entry.getIface() != inIface) {
				sendPacket(etherPacket, entry.getIface());
			}
		} else {
			if (entry != null) {
				switchTable.remove(etherPacket.getDestinationMAC());
			}
			for (Iface iface : interfaces.values()) {
				if (iface != inIface) {
					sendPacket(etherPacket, iface);
				}
			}
		}

		switchTable.put(etherPacket.getSourceMAC(), new CachableEntry(inIface));

	}

}

private class CachableEntry {
	private IFace outIface;
	private long expirationTime;

	public CachableEntry(Iface iface) {
		this.outIface = iface;
		this.expirationTime = System.currentTimeMillis() + 15000; // 15 seconds from the current time
	}

	public MACAddress getIFace() {
		return outIface;
	}

	public long getExpirationTime() {
		return expirationTime;
	}

	public boolean isExpired() {
		return System.currentTimeMillis() > expirationTime;
	}

	public void resetExpirationTime() {
		expirationTime = System.currentTimeMillis() + 15000;
	}
}