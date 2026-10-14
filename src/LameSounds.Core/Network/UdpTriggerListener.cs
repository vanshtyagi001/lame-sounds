using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using LameSounds.Core.Audio;
using LameSounds.Core.Models;

namespace LameSounds.Core.Network
{
    public class UdpTriggerListener : IDisposable
    {
        private readonly AudioEngine _audioEngine;
        private UdpClient? _udpClient;
        private CancellationTokenSource? _cts;
        private int _port = 50051;
        private bool _isRunning = false;

        public bool IsRunning => _isRunning;
        public int Port => _port;

        public event EventHandler<(string SoundId, string Action, string RemoteEndpoint)>? TriggerReceived;

        public UdpTriggerListener(AudioEngine audioEngine)
        {
            _audioEngine = audioEngine;
        }

        public void Start(int port = 50051)
        {
            Stop();

            _port = port;
            _cts = new CancellationTokenSource();
            _udpClient = new UdpClient();
            _udpClient.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _udpClient.Client.Bind(new IPEndPoint(IPAddress.Any, _port));
            _isRunning = true;

            Task.Run(() => ListenLoopAsync(_cts.Token));
        }

        private async Task ListenLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _udpClient != null)
            {
                try
                {
                    var result = await _udpClient.ReceiveAsync(ct);
                    var rawString = Encoding.UTF8.GetString(result.Buffer);

                    ProcessDatagram(rawString, result.RemoteEndPoint.ToString());
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[UdpTriggerListener] Error: {ex.Message}");
                }
            }
        }

        private void ProcessDatagram(string payload, string remoteEp)
        {
            try
            {
                var packet = JsonSerializer.Deserialize<UdpTriggerPacket>(payload);
                if (packet == null) return;

                var action = packet.Type?.ToLowerInvariant() ?? "play";
                var soundId = packet.SoundId ?? string.Empty;

                switch (action)
                {
                    case "play":
                        if (!string.IsNullOrEmpty(soundId))
                        {
                            _audioEngine.PlaySound(soundId, packet.Volume);
                        }
                        break;
                    case "stop":
                        if (!string.IsNullOrEmpty(soundId))
                        {
                            _audioEngine.StopSound(soundId);
                        }
                        break;
                    case "stop_all":
                        _audioEngine.StopAll();
                        break;
                    case "volume":
                        float sVol = packet.SpeakerVolume ?? packet.Volume ?? _audioEngine.Config.MonitorVolume;
                        float mVol = packet.MicVolume ?? _audioEngine.Config.BroadcastVolume;
                        _audioEngine.UpdateVolumes(sVol, mVol, mVol);
                        break;
                }

                TriggerReceived?.Invoke(this, (soundId, action, remoteEp));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[UdpTriggerListener] Parse error: {ex.Message}");
            }
        }

        public void Stop()
        {
            _isRunning = false;
            _cts?.Cancel();
            _cts?.Dispose();
            _cts = null;

            _udpClient?.Close();
            _udpClient?.Dispose();
            _udpClient = null;
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
