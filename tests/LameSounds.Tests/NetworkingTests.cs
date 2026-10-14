using System;
using System.IO;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using LameSounds.Core.Audio;
using LameSounds.Core.Network;
using LameSounds.Core.Services;
using Xunit;

namespace LameSounds.Tests
{
    [Collection("SequentialNetworking")]
    public class NetworkingTests : IDisposable
    {
        private readonly string _tempDir;
        private readonly AudioEngine _engine;
        private readonly SoundLibraryService _library;
        private readonly SettingsService _settings;
        private readonly UdpTriggerListener _udpListener;
        private readonly HttpServer _httpServer;

        private readonly int _testUdpPort;
        private readonly int _testHttpPort;

        public NetworkingTests()
        {
            _tempDir = Path.Combine(Path.GetTempPath(), "LameNetTest_" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(_tempDir);

            // Find free ports dynamically
            using (var l = new TcpListener(System.Net.IPAddress.Loopback, 0))
            {
                l.Start();
                _testHttpPort = ((System.Net.IPEndPoint)l.LocalEndpoint).Port;
                l.Stop();
            }

            using (var u = new UdpClient(0))
            {
                _testUdpPort = ((System.Net.IPEndPoint)u.Client.LocalEndPoint!).Port;
            }

            var devMgr = new DeviceManager();
            _engine = new AudioEngine(devMgr);
            _library = new SoundLibraryService(_engine, _tempDir);
            _settings = new SettingsService(_tempDir);

            _udpListener = new UdpTriggerListener(_engine);
            _httpServer = new HttpServer(_engine, _library, _settings);

            _udpListener.Start(_testUdpPort);
            _httpServer.Start(_testHttpPort);
        }

        [Fact]
        public async Task UdpTriggerListener_ReceivesAndDispatchesTrigger()
        {
            var tcs = new TaskCompletionSource<string>();
            _udpListener.TriggerReceived += (s, e) =>
            {
                tcs.TrySetResult(e.SoundId);
            };

            using var client = new UdpClient();
            var payload = "{\"type\":\"play\",\"soundId\":\"sample_1\",\"volume\":1.0}";
            var bytes = Encoding.UTF8.GetBytes(payload);
            await client.SendAsync(bytes, bytes.Length, "127.0.0.1", _testUdpPort);

            var completedTask = await Task.WhenAny(tcs.Task, Task.Delay(2000));
            Assert.Same(tcs.Task, completedTask);
            Assert.Equal("sample_1", await tcs.Task);
        }

        [Fact]
        public async Task HttpServer_ServesApiStatusAndSounds()
        {
            using var http = new HttpClient();
            var statusJson = await http.GetStringAsync($"http://localhost:{_testHttpPort}/api/status");
            Assert.Contains("online", statusJson);

            // Library starts empty now that default sample sounds are removed
            var emptySoundsJson = await http.GetStringAsync($"http://localhost:{_testHttpPort}/api/sounds");
            Assert.Equal("[]", emptySoundsJson.Trim());

            // Add a test sound and verify it is served via API
            var testWav = Path.Combine(_tempDir, "test_api.wav");
            CreateMinimalWavFile(testWav);
            var clip = _library.AddSound(testWav, "API Clip", category: "MEMES");

            var soundsJson = await http.GetStringAsync($"http://localhost:{_testHttpPort}/api/sounds");
            Assert.Contains(clip.Id, soundsJson);
            Assert.Contains("API Clip", soundsJson);
        }

        private static void CreateMinimalWavFile(string filePath)
        {
            int sampleRate = 44100;
            short channels = 1;
            short bitsPerSample = 16;
            int numSamples = sampleRate / 2; // 0.5 sec
            int subChunk2Size = numSamples * channels * (bitsPerSample / 8);
            int chunkSize = 36 + subChunk2Size;

            using var fs = new FileStream(filePath, FileMode.Create, FileAccess.Write);
            using var bw = new BinaryWriter(fs);
            bw.Write(Encoding.ASCII.GetBytes("RIFF"));
            bw.Write(chunkSize);
            bw.Write(Encoding.ASCII.GetBytes("WAVE"));
            bw.Write(Encoding.ASCII.GetBytes("fmt "));
            bw.Write(16);
            bw.Write((short)1); // PCM
            bw.Write(channels);
            bw.Write(sampleRate);
            bw.Write(sampleRate * channels * (bitsPerSample / 8));
            bw.Write((short)(channels * (bitsPerSample / 8)));
            bw.Write(bitsPerSample);
            bw.Write(Encoding.ASCII.GetBytes("data"));
            bw.Write(subChunk2Size);
            for (int i = 0; i < numSamples; i++)
            {
                bw.Write((short)0);
            }
        }

        [Fact]
        public void NetworkManager_GeneratesValidQrCode()
        {
            var pngBytes = NetworkManager.GenerateQrCodePngBytes("192.168.1.50", 50050, 50051);
            Assert.NotNull(pngBytes);
            Assert.True(pngBytes.Length > 100);

            // PNG magic header check (0x89, 'P', 'N', 'G')
            Assert.Equal(0x89, pngBytes[0]);
            Assert.Equal(0x50, pngBytes[1]);
            Assert.Equal(0x4E, pngBytes[2]);
            Assert.Equal(0x47, pngBytes[3]);
        }

        [Fact]
        public async Task HttpServer_ServesAudioFileDownload()
        {
            var testWav = Path.Combine(_tempDir, "download_test.wav");
            CreateMinimalWavFile(testWav);
            var clip = _library.AddSound(testWav, "Download Clip", category: "CUSTOM");

            using var http = new HttpClient();
            var audioBytes = await http.GetByteArrayAsync($"http://localhost:{_testHttpPort}/api/sounds/{clip.Id}/file");

            Assert.NotNull(audioBytes);
            Assert.True(audioBytes.Length > 0);
            // Check RIFF header
            Assert.Equal((byte)'R', audioBytes[0]);
            Assert.Equal((byte)'I', audioBytes[1]);
            Assert.Equal((byte)'F', audioBytes[2]);
            Assert.Equal((byte)'F', audioBytes[3]);
        }

        [Fact]
        public async Task HttpServer_HandlesAudioUploadAndDeduplication()
        {
            var testWav = Path.Combine(_tempDir, "upload_test.wav");
            CreateMinimalWavFile(testWav);
            var wavBytes = await File.ReadAllBytesAsync(testWav);
            var base64 = Convert.ToBase64String(wavBytes);

            using var http = new HttpClient();
            var payload = System.Text.Json.JsonSerializer.Serialize(new
            {
                title = "Uploaded Boom",
                fileName = "boom.wav",
                category = "Phone",
                fileSizeBytes = wavBytes.Length,
                base64Audio = base64
            });

            // 1. Initial upload should succeed
            var content1 = new StringContent(payload, Encoding.UTF8, "application/json");
            var resp1 = await http.PostAsync($"http://localhost:{_testHttpPort}/api/sounds/upload", content1);
            var body1 = await resp1.Content.ReadAsStringAsync();
            Assert.True(resp1.IsSuccessStatusCode);
            Assert.Contains("imported", body1);

            // 2. Duplicate upload with same title and file size should be skipped
            var content2 = new StringContent(payload, Encoding.UTF8, "application/json");
            var resp2 = await http.PostAsync($"http://localhost:{_testHttpPort}/api/sounds/upload", content2);
            var body2 = await resp2.Content.ReadAsStringAsync();
            Assert.True(resp2.IsSuccessStatusCode);
            Assert.Contains("duplicate_skipped", body2);
        }

        [Fact]
        public void SoundLibraryService_DeduplicationDetectsDuplicates()
        {
            var testWav = Path.Combine(_tempDir, "dedup_test.wav");
            CreateMinimalWavFile(testWav);
            var clip = _library.AddSound(testWav, "Unique Sound", category: "CUSTOM");

            // Same title + same size = duplicate
            Assert.True(_library.IsDuplicate("Unique Sound", "other.wav", clip.FileSizeBytes));
            // Case-insensitive title + same size = duplicate
            Assert.True(_library.IsDuplicate("unique sound", "other.wav", clip.FileSizeBytes));
            // Same title + different size = not duplicate
            Assert.False(_library.IsDuplicate("Unique Sound", "other.wav", clip.FileSizeBytes + 500));
            // Different title + same size = not duplicate
            Assert.False(_library.IsDuplicate("Brand New Sound", "brand_new.wav", clip.FileSizeBytes));
        }

        public void Dispose()
        {
            _udpListener.Dispose();
            _httpServer.Dispose();
            _engine.Dispose();

            try
            {
                if (Directory.Exists(_tempDir))
                {
                    Directory.Delete(_tempDir, true);
                }
            }
            catch { }
        }
    }
}
