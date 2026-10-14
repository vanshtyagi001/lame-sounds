using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using LameSounds.Core.Audio;
using LameSounds.Core.Models;
using LameSounds.Core.Services;

namespace LameSounds.Core.Network
{
    /// <summary>
    /// Lightweight embedded HTTP server built on TcpListener (zero Windows URLACL / admin permission requirements).
    /// </summary>
    public class HttpServer : IDisposable
    {
        private readonly AudioEngine _audioEngine;
        private readonly SoundLibraryService _soundLibrary;
        private readonly SettingsService _settingsService;
        private TcpListener? _tcpListener;
        private CancellationTokenSource? _cts;
        private int _port = 50050;
        private bool _isRunning = false;

        public bool IsRunning => _isRunning;
        public int Port => _port;

        public string? LastPhoneIp { get; private set; }
        public int LastPhonePort { get; private set; } = 50055;
        public int LastPhoneSoundsCount { get; private set; }
        public DateTime? LastPhoneSeen { get; private set; }
        public event EventHandler? PhoneRegistered;

        public HttpServer(AudioEngine audioEngine, SoundLibraryService soundLibrary, SettingsService settingsService)
        {
            _audioEngine = audioEngine;
            _soundLibrary = soundLibrary;
            _settingsService = settingsService;
        }

        public void Start(int port = 50050)
        {
            Stop();

            _port = port;
            _cts = new CancellationTokenSource();

            try
            {
                _tcpListener = new TcpListener(IPAddress.Any, _port);
                _tcpListener.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _tcpListener.Start();
                _isRunning = true;

                Task.Run(() => AcceptClientsLoopAsync(_cts.Token));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[HttpServer] Failed to start: {ex.Message}");
                _isRunning = false;
            }
        }

        private async Task AcceptClientsLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _tcpListener != null)
            {
                try
                {
                    var client = await _tcpListener.AcceptTcpClientAsync(ct);
                    _ = Task.Run(() => HandleClientAsync(client, ct), ct);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[HttpServer] Accept error: {ex.Message}");
                }
            }
        }

        private async Task HandleClientAsync(TcpClient client, CancellationToken ct)
        {
            using (client)
            using (var stream = client.GetStream())
            {
                stream.ReadTimeout = 5000;
                stream.WriteTimeout = 5000;

                try
                {
                    var reader = new StreamReader(stream, Encoding.UTF8);
                    var requestLine = await reader.ReadLineAsync(ct);
                    if (string.IsNullOrWhiteSpace(requestLine)) return;

                    var parts = requestLine.Split(' ');
                    if (parts.Length < 2) return;

                    var method = parts[0].ToUpperInvariant();
                    var rawUrl = parts[1];
                    var path = rawUrl.Split('?')[0].TrimEnd('/').ToLowerInvariant();

                    // Read headers
                    int contentLength = 0;
                    string? line;
                    while (!string.IsNullOrEmpty(line = await reader.ReadLineAsync(ct)))
                    {
                        if (line.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase))
                        {
                            int.TryParse(line.Substring(15).Trim(), out contentLength);
                        }
                    }

                    // Read body if POST
                    string body = string.Empty;
                    if (contentLength > 0)
                    {
                        var bodyChars = new char[contentLength];
                        int totalRead = 0;
                        while (totalRead < contentLength)
                        {
                            int read = await reader.ReadAsync(bodyChars, totalRead, contentLength - totalRead);
                            if (read <= 0) break;
                            totalRead += read;
                        }
                        body = new string(bodyChars, 0, totalRead);
                    }

                    // Handle CORS Preflight
                    if (method == "OPTIONS")
                    {
                        await SendResponseAsync(stream, 200, "text/plain", Encoding.UTF8.GetBytes("OK"));
                        return;
                    }

                    // Route Requests
                    if (path == "" || path == "/index.html")
                    {
                        var html = GetWebCompanionHtml();
                        await SendResponseAsync(stream, 200, "text/html; charset=utf-8", Encoding.UTF8.GetBytes(html));
                    }
                    else if (path == "/api/sounds" && method == "GET")
                    {
                        var sounds = _soundLibrary.Sounds;
                        var json = JsonSerializer.Serialize(sounds);
                        await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(json));
                    }
                    else if (path == "/api/sounds/trigger" && method == "POST")
                    {
                        var packet = JsonSerializer.Deserialize<UdpTriggerPacket>(body);
                        if (packet != null && !string.IsNullOrEmpty(packet.SoundId))
                        {
                            _audioEngine.PlaySound(packet.SoundId, packet.Volume);
                            var resp = JsonSerializer.Serialize(new { status = "ok", played = packet.SoundId });
                            await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(resp));
                        }
                        else
                        {
                            var err = JsonSerializer.Serialize(new { error = "Invalid soundId" });
                            await SendResponseAsync(stream, 400, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(err));
                        }
                    }
                    else if (path == "/api/sounds/stop" && method == "POST")
                    {
                        var packet = !string.IsNullOrEmpty(body) ? JsonSerializer.Deserialize<UdpTriggerPacket>(body) : null;
                        if (packet != null && !string.IsNullOrEmpty(packet.SoundId))
                        {
                            _audioEngine.StopSound(packet.SoundId);
                        }
                        else
                        {
                            _audioEngine.StopAll();
                        }
                        var resp = JsonSerializer.Serialize(new { status = "ok" });
                        await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(resp));
                    }
                    else if (path == "/api/status" && method == "GET")
                    {
                        var localEp = client.Client.LocalEndPoint as IPEndPoint;
                        var ip = localEp?.Address?.ToString();
                        if (string.IsNullOrEmpty(ip) || ip == "0.0.0.0" || ip == "127.0.0.1")
                        {
                            ip = NetworkManager.GetLocalIpAddress();
                        }

                        var status = new
                        {
                            status = "online",
                            machine = Environment.MachineName,
                            ip = ip,
                            httpPort = _port,
                            udpPort = _settingsService.Config.UdpPort,
                            soundsCount = _soundLibrary.Sounds.Count,
                            virtualCableDetected = _audioEngine.DeviceManager.IsVirtualCableInstalled()
                        };
                        var json = JsonSerializer.Serialize(status);
                        await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(json));
                    }
                    else if (path == "/api/volume" && method == "GET")
                    {
                        var volResp = new
                        {
                            speakerVolume = _audioEngine.Config.MonitorVolume,
                            micVolume = _audioEngine.Config.BroadcastVolume
                        };
                        var json = JsonSerializer.Serialize(volResp);
                        await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(json));
                    }
                    else if (path == "/api/volume" && method == "POST")
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(body);
                            var root = doc.RootElement;
                            float speaker = root.TryGetProperty("speakerVolume", out var s) ? (float)s.GetDouble() : _audioEngine.Config.MonitorVolume;
                            float mic = root.TryGetProperty("micVolume", out var m) ? (float)m.GetDouble() : _audioEngine.Config.BroadcastVolume;

                            _audioEngine.UpdateVolumes(speaker, mic, mic);
                            var resp = JsonSerializer.Serialize(new { status = "ok", speakerVolume = speaker, micVolume = mic });
                            await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(resp));
                        }
                        catch
                        {
                            var err = JsonSerializer.Serialize(new { error = "Invalid volume format" });
                            await SendResponseAsync(stream, 400, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(err));
                        }
                    }
                    else if ((path == "/api/play" || path == "/api/sounds/play") && method == "POST")
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(body);
                            var root = doc.RootElement;
                            var soundId = root.GetProperty("soundId").GetString();
                            float? vol = root.TryGetProperty("volume", out var v) ? (float?)v.GetDouble() : null;
                            if (!string.IsNullOrEmpty(soundId))
                            {
                                _audioEngine.PlaySound(soundId, vol);
                            }
                            var resp = JsonSerializer.Serialize(new { status = "playing", soundId = soundId });
                            await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(resp));
                        }
                        catch (Exception ex)
                        {
                            var err = JsonSerializer.Serialize(new { error = ex.Message });
                            await SendResponseAsync(stream, 400, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(err));
                        }
                    }
                    else if ((path == "/api/stop" || path == "/api/sounds/stop" || path == "/api/stop_all") && method == "POST")
                    {
                        _audioEngine.StopAll();
                        var resp = JsonSerializer.Serialize(new { status = "stopped" });
                        await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(resp));
                    }
                    else if (path == "/api/qr" && method == "GET")
                    {
                        var ip = NetworkManager.GetLocalIpAddress();
                        var png = NetworkManager.GenerateQrCodePngBytes(ip, _port, _settingsService.Config.UdpPort);
                        await SendResponseAsync(stream, 200, "image/png", png);
                    }
                    else if (((path.StartsWith("/api/sounds/") && path.EndsWith("/file")) || path == "/api/sounds/download") && method == "GET")
                    {
                        string? soundId = null;
                        if (path.StartsWith("/api/sounds/") && path.EndsWith("/file"))
                        {
                            var segs = path.Split('/');
                            if (segs.Length >= 4) soundId = segs[3];
                        }
                        else if (rawUrl.Contains("id="))
                        {
                            var idx = rawUrl.IndexOf("id=") + 3;
                            var endIdx = rawUrl.IndexOf('&', idx);
                            soundId = endIdx > 0 ? rawUrl.Substring(idx, endIdx - idx) : rawUrl.Substring(idx);
                        }

                        if (!string.IsNullOrEmpty(soundId))
                        {
                            var clip = _soundLibrary.GetSound(soundId);
                            if (clip != null && !string.IsNullOrEmpty(clip.FilePath) && File.Exists(clip.FilePath))
                            {
                                var ext = Path.GetExtension(clip.FilePath).ToLowerInvariant();
                                string mime = "audio/mpeg";
                                if (ext == ".wav") mime = "audio/wav";
                                else if (ext == ".ogg") mime = "audio/ogg";
                                else if (ext == ".flac") mime = "audio/flac";
                                else if (ext == ".aac") mime = "audio/aac";
                                else if (ext == ".m4a") mime = "audio/mp4";
                                var bytes = await File.ReadAllBytesAsync(clip.FilePath, ct);
                                await SendResponseAsync(stream, 200, mime, bytes);
                                return;
                            }
                        }
                        await SendResponseAsync(stream, 404, "application/json; charset=utf-8", Encoding.UTF8.GetBytes("{\"error\":\"Sound file not found\"}"));
                    }
                    else if (((path.StartsWith("/api/sounds/") && path.EndsWith("/image")) || path == "/api/sounds/image") && method == "GET")
                    {
                        string? soundId = null;
                        if (path.StartsWith("/api/sounds/") && path.EndsWith("/image"))
                        {
                            var segs = path.Split('/');
                            if (segs.Length >= 4) soundId = segs[3];
                        }
                        else if (rawUrl.Contains("id="))
                        {
                            var idx = rawUrl.IndexOf("id=") + 3;
                            var endIdx = rawUrl.IndexOf('&', idx);
                            soundId = endIdx > 0 ? rawUrl.Substring(idx, endIdx - idx) : rawUrl.Substring(idx);
                        }

                        if (!string.IsNullOrEmpty(soundId))
                        {
                            var clip = _soundLibrary.GetSound(soundId);
                            if (clip != null && !string.IsNullOrEmpty(clip.ImagePath) && File.Exists(clip.ImagePath))
                            {
                                var ext = Path.GetExtension(clip.ImagePath).ToLowerInvariant();
                                string mime = "image/png";
                                if (ext == ".jpg" || ext == ".jpeg") mime = "image/jpeg";
                                else if (ext == ".webp") mime = "image/webp";
                                else if (ext == ".bmp") mime = "image/bmp";
                                var bytes = await File.ReadAllBytesAsync(clip.ImagePath, ct);
                                await SendResponseAsync(stream, 200, mime, bytes);
                                return;
                            }
                        }
                        await SendResponseAsync(stream, 404, "application/json; charset=utf-8", Encoding.UTF8.GetBytes("{\"error\":\"Image not found\"}"));
                    }
                    else if (path == "/api/phone/register" && method == "POST")
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(body);
                            var root = doc.RootElement;
                            var phoneIp = root.TryGetProperty("phoneIp", out var pIp) ? pIp.GetString() : null;
                            var phonePort = root.TryGetProperty("phonePort", out var pPort) ? pPort.GetInt32() : 50055;
                            var soundsCount = root.TryGetProperty("soundsCount", out var sc) ? sc.GetInt32() : 0;

                            if (string.IsNullOrEmpty(phoneIp) && client.Client.RemoteEndPoint is IPEndPoint rep)
                            {
                                phoneIp = rep.Address.ToString();
                            }

                            if (!string.IsNullOrEmpty(phoneIp))
                            {
                                LastPhoneIp = phoneIp;
                                LastPhonePort = phonePort;
                                LastPhoneSoundsCount = soundsCount;
                                LastPhoneSeen = DateTime.UtcNow;
                                PhoneRegistered?.Invoke(this, EventArgs.Empty);
                            }

                            var resp = JsonSerializer.Serialize(new { status = "registered", phoneIp = phoneIp, phonePort = phonePort });
                            await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(resp));
                        }
                        catch (Exception ex)
                        {
                            var err = JsonSerializer.Serialize(new { error = ex.Message });
                            await SendResponseAsync(stream, 400, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(err));
                        }
                    }
                    else if (path == "/api/sounds/upload" && method == "POST")
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(body);
                            var root = doc.RootElement;
                            var title = root.TryGetProperty("title", out var t) ? t.GetString() : "Uploaded Sound";
                            var fileName = root.TryGetProperty("fileName", out var fn) ? fn.GetString() : "sound.mp3";
                            var category = root.TryGetProperty("category", out var c) ? c.GetString() : "Phone";
                            var fileSizeBytes = root.TryGetProperty("fileSizeBytes", out var fsb) ? fsb.GetInt64() : 0;
                            var base64 = root.TryGetProperty("base64Audio", out var b64) ? b64.GetString() : null;

                            if (string.IsNullOrEmpty(base64))
                            {
                                await SendResponseAsync(stream, 400, "application/json; charset=utf-8", Encoding.UTF8.GetBytes("{\"error\":\"base64Audio required\"}"));
                                return;
                            }

                            var audioBytes = Convert.FromBase64String(base64);
                            if (fileSizeBytes <= 0) fileSizeBytes = audioBytes.Length;

                            // Check deduplication
                            if (_soundLibrary.IsDuplicate(title ?? "", fileName, fileSizeBytes))
                            {
                                var dupResp = JsonSerializer.Serialize(new { status = "duplicate_skipped", title = title, reason = "Matching sound exists" });
                                await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(dupResp));
                                return;
                            }

                            // Save to temp file and import
                            var ext = Path.GetExtension(fileName);
                            if (string.IsNullOrEmpty(ext)) ext = ".mp3";
                            var tempFile = Path.Combine(Path.GetTempPath(), $"upload_{Guid.NewGuid():N}{ext}");
                            await File.WriteAllBytesAsync(tempFile, audioBytes, ct);

                            var clip = _soundLibrary.AddSound(tempFile, title ?? "Phone Sound", category: category);
                            try { File.Delete(tempFile); } catch { }

                            var okResp = JsonSerializer.Serialize(new { status = "imported", id = clip.Id, title = clip.Title });
                            await SendResponseAsync(stream, 200, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(okResp));
                        }
                        catch (Exception ex)
                        {
                            var err = JsonSerializer.Serialize(new { error = ex.Message });
                            await SendResponseAsync(stream, 400, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(err));
                        }
                    }
                    else
                    {
                        var err = JsonSerializer.Serialize(new { error = "Not found" });
                        await SendResponseAsync(stream, 404, "application/json; charset=utf-8", Encoding.UTF8.GetBytes(err));
                    }
                }
                catch { }
            }
        }

        private static async Task SendResponseAsync(NetworkStream stream, int statusCode, string contentType, byte[] body)
        {
            var statusText = statusCode switch
            {
                200 => "OK",
                400 => "Bad Request",
                404 => "Not Found",
                500 => "Internal Server Error",
                _ => "OK"
            };

            var header = $"HTTP/1.1 {statusCode} {statusText}\r\n" +
                         $"Content-Type: {contentType}\r\n" +
                         $"Content-Length: {body.Length}\r\n" +
                         "Connection: close\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                         "Access-Control-Allow-Headers: Content-Type\r\n\r\n";

            var headerBytes = Encoding.UTF8.GetBytes(header);
            await stream.WriteAsync(headerBytes);
            await stream.WriteAsync(body);
            await stream.FlushAsync();
        }

        private string GetWebCompanionHtml()
        {
            var ip = NetworkManager.GetLocalIpAddress();
            return $$"""
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>LameSounds Mobile Remote</title>
  <style>
    :root {
      --bg: #000000;
      --card-bg: #111111;
      --card-border: #1f1f1f;
      --surface-dark: #080808;
      --accent: #ffffff;
      --success: #10b981;
      --warning: #f59e0b;
      --danger: #ef4444;
      --text: #ffffff;
      --text-muted: #a1a1aa;
      --text-faint: #71717a;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      user-select: none;
      overflow-x: hidden;
    }
    /* Top Header */
    header {
      padding: 12px 18px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--surface-dark);
      border-bottom: 1px solid var(--card-border);
      position: sticky;
      top: 0;
      z-index: 20;
    }
    .brand { display: flex; align-items: center; gap: 8px; font-weight: 800; font-size: 0.95rem; letter-spacing: -0.3px; }
    .status-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      color: var(--success);
      font-size: 0.70rem;
      padding: 4px 10px;
      border-radius: 9999px;
      font-weight: 600;
    }
    .status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); }
    .header-actions { display: flex; gap: 8px; align-items: center; }
    .icon-btn {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      color: var(--text);
      width: 34px;
      height: 34px;
      border-radius: 9999px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 0.95rem;
    }

    /* Set Navigation Bar: ← CS2 CALLS → */
    .set-nav-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 16px;
      background: rgba(255, 255, 255, 0.02);
      border-bottom: 1px solid var(--card-border);
    }
    .set-title-group {
      display: flex;
      flex-direction: column;
      align-items: center;
      cursor: pointer;
    }
    .set-title {
      font-size: 1rem;
      font-weight: 900;
      letter-spacing: 1px;
      text-transform: uppercase;
      color: var(--text);
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .grid-tag {
      background: rgba(99, 102, 241, 0.2);
      color: var(--accent);
      font-size: 0.65rem;
      padding: 2px 6px;
      border-radius: 4px;
      font-weight: 700;
    }
    .nav-arrow {
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 1.4rem;
      cursor: pointer;
      padding: 4px 10px;
    }
    .dots {
      display: flex;
      gap: 6px;
      margin-top: 4px;
    }
    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--text-muted);
      opacity: 0.4;
      transition: all 0.2s;
    }
    .dot.active {
      width: 8px;
      background: var(--accent);
      opacity: 1;
    }

    /* Controls Row: Grid Size & Shapes */
    .quick-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 16px;
      background: rgba(0, 0, 0, 0.2);
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .pill-select {
      background: rgba(255, 255, 255, 0.08);
      color: var(--text);
      border: none;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      cursor: pointer;
    }

    /* Sound Grid */
    .viewport {
      flex: 1;
      overflow-y: auto;
      padding: 12px;
      display: flex;
      touch-action: pan-y pinch-zoom;
    }
    .grid {
      display: grid;
      width: 100%;
      gap: 10px;
      align-content: start;
    }
    /* Grid Size Columns */
    .cols-2 { grid-template-columns: repeat(2, 1fr); }
    .cols-3 { grid-template-columns: repeat(3, 1fr); }
    .cols-4 { grid-template-columns: repeat(4, 1fr); }
    .cols-5 { grid-template-columns: repeat(5, 1fr); }

    /* Button Card Styling */
    .sound-button {
      aspect-ratio: 1 / 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: space-between;
      padding: 10px 8px;
      text-align: center;
      cursor: pointer;
      border: 1px solid rgba(255, 255, 255, 0.08);
      transition: transform 0.08s ease, filter 0.15s ease;
      position: relative;
      overflow: hidden;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    }
    .sound-button:active {
      transform: scale(0.92);
      filter: brightness(1.25);
    }
    /* Button Shapes */
    .shape-rounded { border-radius: 14px; }
    .shape-circle { border-radius: 50%; }
    .shape-rect { border-radius: 4px; }
    .shape-hex {
      clip-path: polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%);
      border-radius: 0;
    }

    .btn-label {
      font-size: 0.82rem;
      font-weight: 700;
      line-height: 1.15;
      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.7);
    }
    .btn-icon {
      font-size: 1.3rem;
      opacity: 0.9;
    }

    /* Bottom Volume Controller */
    .bottom-bar {
      position: sticky;
      bottom: 0;
      background: rgba(22, 29, 46, 0.95);
      backdrop-filter: blur(14px);
      border-top: 1px solid var(--card-border);
      padding: 10px 16px;
      display: flex;
      gap: 16px;
      z-index: 20;
    }
    .vol-box {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .vol-header {
      display: flex;
      justify-content: space-between;
      font-size: 0.72rem;
      font-weight: 700;
      color: var(--text-muted);
    }
    .vol-slider {
      width: 100%;
      accent-color: var(--accent);
      cursor: pointer;
      height: 4px;
    }
    .vol-slider.mic { accent-color: var(--success); }

    /* Search Modal */
    .modal-overlay {
      display: none;
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(8px);
      z-index: 100;
      padding: 20px;
    }
    .modal-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      max-height: 80vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .search-input {
      background: var(--bg);
      border: 1px solid var(--card-border);
      color: var(--text);
      padding: 12px 16px;
      font-size: 1rem;
      outline: none;
    }
    .search-list {
      overflow-y: auto;
      padding: 10px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .search-item {
      background: rgba(255, 255, 255, 0.04);
      padding: 12px;
      border-radius: 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
    }
  </style>
</head>
<body>
  <!-- Top Bar -->
  <header>
    <div class="brand">
      <span>🔊</span>
      <span>LameSounds</span>
    </div>
    <div class="status-badge" id="statusBadge">
      <div class="status-dot"></div>
      <span id="latencyText">Connected • 18ms</span>
    </div>
    <div class="header-actions">
      <button class="icon-btn" onclick="openSearch()">🔍</button>
      <button class="icon-btn" style="color: var(--danger);" onclick="stopAll()">⏹</button>
    </div>
  </header>

  <!-- Sound Set Header Navigation: ← CS2 CALLS → -->
  <div class="set-nav-bar">
    <button class="nav-arrow" onclick="prevSet()">‹</button>
    <div class="set-title-group" onclick="cycleGrid()">
      <div class="set-title">
        <span id="currentSetName">CS2 CALLS</span>
        <span class="grid-tag" id="gridDimBadge">3×3</span>
      </div>
      <div class="dots" id="dotsContainer"></div>
    </div>
    <button class="nav-arrow" onclick="nextSet()">›</button>
  </div>

  <!-- Customization Controls Row -->
  <div class="quick-bar">
    <span>One-Tap = Play</span>
    <div style="display: flex; gap: 8px;">
      <select class="pill-select" id="shapeSelector" onchange="changeShape(this.value)">
        <option value="shape-rounded">Rounded</option>
        <option value="shape-hex">Hexagon</option>
        <option value="shape-circle">Circle</option>
        <option value="shape-rect">Rectangle</option>
      </select>
      <select class="pill-select" id="gridSelector" onchange="changeGrid(this.value)">
        <option value="cols-3">3×3 Grid</option>
        <option value="cols-2">2×2 Grid</option>
        <option value="cols-4">4×4 Grid</option>
        <option value="cols-5">5×5 Grid</option>
      </select>
    </div>
  </div>

  <!-- Soundboard Grid Viewport -->
  <div class="viewport" id="viewport">
    <div class="grid cols-3" id="soundGrid"></div>
  </div>

  <!-- Accessible Bottom Volume Bar -->
  <div class="bottom-bar">
    <div class="vol-box">
      <div class="vol-header">
        <span>🎧 Speaker (Headphones)</span>
        <span id="speakerVal">100%</span>
      </div>
      <input type="range" class="vol-slider" min="0" max="1" step="0.02" value="1.0" id="speakerSlider" oninput="onVolumeChange()">
    </div>
    <div class="vol-box">
      <div class="vol-header">
        <span>🎙️ Mic (CS2 Game)</span>
        <span id="micVal">100%</span>
      </div>
      <input type="range" class="vol-slider mic" min="0" max="1" step="0.02" value="1.0" id="micSlider" oninput="onVolumeChange()">
    </div>
  </div>

  <!-- Search Modal Overlay -->
  <div class="modal-overlay" id="searchModal" onclick="if(event.target === this) closeSearch()">
    <div class="modal-card">
      <input type="text" class="search-input" id="searchInput" placeholder="Search all sounds (e.g. rush, bomb)..." oninput="filterSearch(this.value)">
      <div class="search-list" id="searchList"></div>
    </div>
  </div>

  <script>
    const soundSets = [
      {
        name: "CS2 CALLS",
        buttons: [
          { id: "sample_1", label: "Rush B", color: "#EF4444", icon: "💣" },
          { id: "sample_2", label: "Enemy Spotted", color: "#F59E0B", icon: "🎯" },
          { id: "sample_3", label: "Hold Site", color: "#10B981", icon: "🛡️" },
          { id: "sample_4", label: "Need Help", color: "#3B82F6", icon: "🚨" },
          { id: "sample_5", label: "Go Go Go", color: "#6366F1", icon: "⚡" },
          { id: "sample_6", label: "Fall Back", color: "#8B5CF6", icon: "⬅️" },
          { id: "sample_1", label: "Nice Shot", color: "#EC4899", icon: "🔥" },
          { id: "sample_2", label: "Save Weapons", color: "#14B8A6", icon: "💰" },
          { id: "sample_3", label: "Plant Bomb", color: "#E11D48", icon: "⏱️" }
        ]
      },
      {
        name: "MEMES",
        buttons: [
          { id: "sample_1", label: "Airhorn", color: "#EF4444", icon: "📢" },
          { id: "sample_2", label: "Victory", color: "#F59E0B", icon: "🏆" },
          { id: "sample_3", label: "Game Over", color: "#6366F1", icon: "💀" },
          { id: "sample_4", label: "Ding", color: "#10B981", icon: "🔔" },
          { id: "sample_5", label: "Applause", color: "#EC4899", icon: "👏" },
          { id: "sample_6", label: "Laser Zap", color: "#06B6D4", icon: "⚡" }
        ]
      },
      {
        name: "REACTIONS",
        buttons: [
          { id: "sample_1", label: "GG", color: "#10B981", icon: "👍" },
          { id: "sample_3", label: "Oof", color: "#EF4444", icon: "💥" },
          { id: "sample_5", label: "Cheer", color: "#F59E0B", icon: "🎉" },
          { id: "sample_4", label: "Notice", color: "#3B82F6", icon: "💡" }
        ]
      }
    ];

    let currentSetIndex = 0;
    let currentShape = 'shape-rounded';
    let currentGrid = 'cols-3';

    // Touch swipe handling
    let touchStartX = 0;
    const viewport = document.getElementById('viewport');
    viewport.addEventListener('touchstart', e => { touchStartX = e.changedTouches[0].screenX; }, {passive: true});
    viewport.addEventListener('touchend', e => {
      const diff = e.changedTouches[0].screenX - touchStartX;
      if (diff > 50) prevSet();
      else if (diff < -50) nextSet();
    }, {passive: true});

    function renderSet() {
      const set = soundSets[currentSetIndex];
      document.getElementById('currentSetName').textContent = set.name;
      const cols = currentGrid.replace('cols-', '');
      document.getElementById('gridDimBadge').textContent = `${cols}×${cols}`;

      // Render Dots
      const dotsContainer = document.getElementById('dotsContainer');
      dotsContainer.innerHTML = soundSets.map((_, i) =>
        `<div class="dot ${i === currentSetIndex ? 'active' : ''}" onclick="goToSet(${i})"></div>`
      ).join('');

      // Render Buttons
      const grid = document.getElementById('soundGrid');
      grid.className = `grid ${currentGrid}`;
      grid.innerHTML = set.buttons.map(b => `
        <div class="sound-button ${currentShape}"
             style="background: linear-gradient(135deg, ${b.color}, #1f242c)"
             onclick="playSound('${b.id}')">
          <div class="btn-icon">${b.icon || '▶'}</div>
          <div class="btn-label">${b.label}</div>
        </div>
      `).join('');
    }

    function prevSet() {
      if (currentSetIndex > 0) { currentSetIndex--; renderSet(); }
    }
    function nextSet() {
      if (currentSetIndex < soundSets.size - 1 || currentSetIndex < soundSets.length - 1) {
        currentSetIndex++;
        renderSet();
      }
    }
    function goToSet(i) { currentSetIndex = i; renderSet(); }

    function changeShape(shape) { currentShape = shape; renderSet(); }
    function changeGrid(grid) { currentGrid = grid; renderSet(); }

    async function playSound(id) {
      if (navigator.vibrate) navigator.vibrate(20);
      try {
        await fetch('/api/sounds/trigger', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ soundId: id, volume: 1.0 })
        });
      } catch (e) { console.error(e); }
    }

    async function stopAll() {
      if (navigator.vibrate) navigator.vibrate([20, 30, 20]);
      try {
        await fetch('/api/sounds/stop', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ soundId: '' })
        });
      } catch (e) { console.error(e); }
    }

    // Volume Syncing
    async function onVolumeChange() {
      const speaker = parseFloat(document.getElementById('speakerSlider').value);
      const mic = parseFloat(document.getElementById('micSlider').value);
      document.getElementById('speakerVal').textContent = Math.round(speaker * 100) + '%';
      document.getElementById('micVal').textContent = Math.round(mic * 100) + '%';

      try {
        await fetch('/api/volume', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ speakerVolume: speaker, micVolume: mic })
        });
      } catch (e) { }
    }

    // Search
    function openSearch() {
      document.getElementById('searchModal').style.display = 'block';
      document.getElementById('searchInput').focus();
      filterSearch('');
    }
    function closeSearch() {
      document.getElementById('searchModal').style.display = 'none';
    }
    function filterSearch(q) {
      const query = q.toLowerCase();
      const allButtons = soundSets.flatMap(s => s.buttons);
      const filtered = query ? allButtons.filter(b => b.label.toLowerCase().includes(query)) : allButtons;
      const list = document.getElementById('searchList');
      list.innerHTML = filtered.map(b => `
        <div class="search-item" onclick="playSound('${b.id}'); closeSearch();">
          <div style="display:flex; align-items:center; gap:8px;">
            <span>${b.icon || '🔊'}</span>
            <strong>${b.label}</strong>
          </div>
          <span style="font-size:0.75rem; color:var(--accent);">▶ Tap to Play</span>
        </div>
      `).join('');
    }

    renderSet();
  </script>
</body>
</html>
""";
        }

        public void Stop()
        {
            _isRunning = false;
            _cts?.Cancel();
            _cts?.Dispose();
            _cts = null;

            if (_tcpListener != null)
            {
                try
                {
                    _tcpListener.Stop();
                }
                catch { }
                _tcpListener = null;
            }
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
