using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using QRCoder;

namespace LameSounds.Core.Network
{
    public static class NetworkManager
    {
        public static string GetLocalIpAddress()
        {
            try
            {
                var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                    .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                                n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                    .ToList();

                // 1. If Windows Mobile Hotspot (192.168.137.1) or Wi-Fi Direct is active, prioritize it
                // because mobile devices connect through the hotspot adapter
                var hotspotIface = interfaces.FirstOrDefault(iface =>
                {
                    var props = iface.GetIPProperties();
                    return props.UnicastAddresses.Any(u => u.Address.ToString().StartsWith("192.168.137."));
                });

                if (hotspotIface != null)
                {
                    var unicast = hotspotIface.GetIPProperties().UnicastAddresses
                        .FirstOrDefault(u => u.Address.AddressFamily == AddressFamily.InterNetwork);
                    if (unicast != null) return unicast.Address.ToString();
                }

                // 2. Otherwise prefer active Wi-Fi or Ethernet
                var ordered = interfaces.OrderByDescending(n => n.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? 2 : 1);
                foreach (var iface in ordered)
                {
                    var props = iface.GetIPProperties();
                    var unicast = props.UnicastAddresses
                        .FirstOrDefault(u => u.Address.AddressFamily == AddressFamily.InterNetwork &&
                                             !IPAddress.IsLoopback(u.Address));

                    if (unicast != null)
                    {
                        return unicast.Address.ToString();
                    }
                }
            }
            catch { }

            return "127.0.0.1";
        }

        public static List<IPAddress> GetBroadcastAddresses()
        {
            var list = new List<IPAddress> { IPAddress.Broadcast };
            try
            {
                var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                    .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                                n.NetworkInterfaceType != NetworkInterfaceType.Loopback);

                foreach (var iface in interfaces)
                {
                    var props = iface.GetIPProperties();
                    foreach (var unicast in props.UnicastAddresses)
                    {
                        if (unicast.Address.AddressFamily == AddressFamily.InterNetwork &&
                            !IPAddress.IsLoopback(unicast.Address) &&
                            unicast.IPv4Mask != null)
                        {
                            var ipBytes = unicast.Address.GetAddressBytes();
                            var maskBytes = unicast.IPv4Mask.GetAddressBytes();
                            var broadcastBytes = new byte[ipBytes.Length];
                            for (int i = 0; i < ipBytes.Length; i++)
                            {
                                broadcastBytes[i] = (byte)(ipBytes[i] | ~maskBytes[i]);
                            }
                            var bcast = new IPAddress(broadcastBytes);
                            if (!list.Contains(bcast))
                            {
                                list.Add(bcast);
                            }
                        }
                    }
                }
            }
            catch { }

            return list;
        }

        public static string GenerateQrCodeSvg(string ip, int httpPort, int udpPort)
        {
            var payload = $"http://{ip}:{httpPort}";
            using var qrGenerator = new QRCodeGenerator();
            using var qrData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.Q);
            using var qrCode = new SvgQRCode(qrData);
            return qrCode.GetGraphic(20);
        }

        public static byte[] GenerateQrCodePngBytes(string ip, int httpPort, int udpPort)
        {
            var payload = $"http://{ip}:{httpPort}";
            using var qrGenerator = new QRCodeGenerator();
            using var qrData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.Q);
            using var qrCode = new PngByteQRCode(qrData);
            return qrCode.GetGraphic(20);
        }
    }
}
