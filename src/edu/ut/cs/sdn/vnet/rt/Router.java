package edu.ut.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;

import edu.ut.cs.sdn.vnet.Device;
import edu.ut.cs.sdn.vnet.DumpFile;
import edu.ut.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile) {
		super(host, logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable() {
		return this.routeTable;
	}

	/**
	 * Load a new routing table from a file.
	 * 
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile) {
		if (!routeTable.load(routeTableFile, this)) {
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * 
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile) {
		if (!arpCache.load(arpCacheFile)) {
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
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

		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
			return;

		IPv4 payload = (IPv4) etherPacket.getPayload();

		int checksum = payload.getChecksum();
		payload.resetChecksum();
		byte[] serializedData = payload.serialize();
		payload.deserialize(serializedData, 0, serializedData.length);
		int newChecksum = payload.getChecksum();
		if (checksum != newChecksum) return;

		/* Handle TTL */
		byte currTTL = payload.getTtl();

		// Drop TTL if packet expires
		if (currTTL == 1) return;

		// Decrement TTL
		payload.setTtl(--currTTL);
		payload.resetChecksum();

		/* Check router interface destinations (i.e. drop packets destined to this router) */
		for (Iface iface : getInterfaces().values()) {
			// Drop packet if destined to one of the router's network interfaces
			if (iface.getIpAddress() == payload.getDestinationAddress()) return;
		}

		/* Forward packet */
		// Get next hop
		RouteEntry routeEntry = routeTable.lookup(payload.getDestinationAddress());

		// Drop packets with no route in route table
		if (routeEntry == null || routeEntry.getInterface() == inIface) return;

		// Get new MAC addresses
		int nextHop = routeEntry.getGatewayAddress();
		if (nextHop == 0) {
			nextHop = payload.getDestinationAddress();
		}
		ArpEntry arpEntry = arpCache.lookup(nextHop);
		if (arpEntry == null) return;

		MACAddress newDestMac = arpEntry.getMac();
		MACAddress newSourceMac = routeEntry.getInterface().getMacAddress();

		// Update MAC headers
		etherPacket.setDestinationMACAddress(newDestMac.toBytes());
		etherPacket.setSourceMACAddress(newSourceMac.toBytes());

		// Send packet
		sendPacket(etherPacket, routeEntry.getInterface());
	}
}
