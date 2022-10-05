package edu.ut.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.ut.cs.sdn.vnet.Device;
import edu.ut.cs.sdn.vnet.DumpFile;
import edu.ut.cs.sdn.vnet.Iface;

import java.util.HashMap;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {

	private HashMap<MACAddress, CachableEntry> switchTable;

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile) {
		super(host, logfile);
		this.switchTable = new HashMap<>();
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

		// Learn MAC and port
		switchTable.put(etherPacket.getSourceMAC(), new CachableEntry(inIface));

		// Look for entry in switch table
		CachableEntry entry = switchTable.get(etherPacket.getDestinationMAC());

		// If destination exists in switch table
		if (entry != null && !entry.isExpired()) {
			entry.resetExpirationTime();
			sendPacket(etherPacket, entry.getIface());
		} else {
			// If entry is expired
			if (entry != null) {
				switchTable.remove(etherPacket.getDestinationMAC());
			}

			// Broadcast
			for (Iface iface : interfaces.values()) {
				if (iface != inIface) {
					sendPacket(etherPacket, iface);
				}
			}
		}
	}

	private class CachableEntry {
		private Iface outIface;
		private long expirationTime;

		public CachableEntry(Iface iface) {
			this.outIface = iface;
			this.expirationTime = System.currentTimeMillis() + 15000; // 15 seconds from the current time
		}

		public Iface getIface() {
			return outIface;
		}

		public boolean isExpired() {
			return System.currentTimeMillis() > expirationTime;
		}

		public void resetExpirationTime() {
			expirationTime = System.currentTimeMillis() + 15000;
		}
	}
}
