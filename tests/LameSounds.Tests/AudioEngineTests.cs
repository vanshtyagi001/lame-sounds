using System;
using System.IO;
using LameSounds.Core.Audio;
using LameSounds.Core.Models;
using LameSounds.Core.Services;
using Xunit;

namespace LameSounds.Tests
{
    public class AudioEngineTests
    {
        [Fact]
        public void CachedSound_CreateSynthTone_GeneratesValidSamples()
        {
            var tone = CachedSound.CreateSynthTone(440.0, 0.5);
            Assert.NotNull(tone);
            Assert.NotNull(tone.AudioData);
            Assert.True(tone.AudioData.Length > 0);
            Assert.Equal(48000, tone.WaveFormat.SampleRate);
            Assert.Equal(2, tone.WaveFormat.Channels);
        }

        [Fact]
        public void CachedSoundSampleProvider_ReadsSamplesCorrectly()
        {
            var tone = CachedSound.CreateSynthTone(440.0, 0.1);
            var provider = new CachedSoundSampleProvider(tone, 0.8f);

            var buffer = new float[1024];
            int read = provider.Read(buffer, 0, buffer.Length);

            Assert.True(read > 0);
            Assert.Equal(0.8f, provider.Volume);
        }

        [Fact]
        public void DeviceManager_CanEnumerateAudioDevices()
        {
            var mgr = new DeviceManager();
            var playback = mgr.GetPlaybackDevices();
            var capture = mgr.GetCaptureDevices();

            Assert.NotNull(playback);
            Assert.NotNull(capture);
            // On any Windows PC with sound, playback devices are enumerated
            Assert.NotEmpty(playback);
        }
    }

    public class SoundLibraryTests : IDisposable
    {
        private readonly string _tempDir;

        public SoundLibraryTests()
        {
            _tempDir = Path.Combine(Path.GetTempPath(), "LameSoundsTest_" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(_tempDir);
        }

        [Fact]
        public void SoundLibrary_StartsEmptyOnFirstRun()
        {
            var devMgr = new DeviceManager();
            using var engine = new AudioEngine(devMgr);
            var library = new SoundLibraryService(engine, _tempDir);

            Assert.Empty(library.Sounds);
        }

        [Fact]
        public void SoundLibrary_CanAddAndRemoveSound()
        {
            var devMgr = new DeviceManager();
            using var engine = new AudioEngine(devMgr);
            var library = new SoundLibraryService(engine, _tempDir);

            int initialCount = library.Sounds.Count;

            // Create temporary test wav
            var testWav = Path.Combine(_tempDir, "custom.wav");
            CreateMinimalWavFile(testWav);

            var added = library.AddSound(testWav, "My Custom Sound");
            Assert.NotNull(added);
            Assert.Equal("My Custom Sound", added.Title);
            Assert.Equal(initialCount + 1, library.Sounds.Count);

            // Remove sound
            bool removed = library.RemoveSound(added.Id);
            Assert.True(removed);
            Assert.Equal(initialCount, library.Sounds.Count);
        }

        private static void CreateMinimalWavFile(string path)
        {
            using var fs = new FileStream(path, FileMode.Create);
            using var wr = new BinaryWriter(fs);
            wr.Write(System.Text.Encoding.ASCII.GetBytes("RIFF"));
            wr.Write(36 + 1000);
            wr.Write(System.Text.Encoding.ASCII.GetBytes("WAVEfmt "));
            wr.Write(16); // subchunk size
            wr.Write((short)1); // PCM
            wr.Write((short)1); // mono
            wr.Write(44100); // sample rate
            wr.Write(88200); // byte rate
            wr.Write((short)2); // block align
            wr.Write((short)16); // bits per sample
            wr.Write(System.Text.Encoding.ASCII.GetBytes("data"));
            wr.Write(1000);
            wr.Write(new byte[1000]);
        }

        public void Dispose()
        {
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
