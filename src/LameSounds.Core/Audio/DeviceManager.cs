using System;
using System.Collections.Generic;
using System.Linq;
using LameSounds.Core.Models;
using NAudio.CoreAudioApi;

namespace LameSounds.Core.Audio
{
    public class DeviceManager
    {
        private readonly MMDeviceEnumerator _enumerator;

        public DeviceManager()
        {
            _enumerator = new MMDeviceEnumerator();
        }

        public List<AudioDeviceInfo> GetPlaybackDevices()
        {
            var list = new List<AudioDeviceInfo>();
            MMDevice? defaultDevice = null;

            try
            {
                defaultDevice = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            }
            catch { }

            var devices = _enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active);
            foreach (var dev in devices)
            {
                var name = dev.FriendlyName;
                var isVirtual = IsVirtualAudioDevice(name);
                var isDefault = defaultDevice != null && dev.ID == defaultDevice.ID;

                list.Add(new AudioDeviceInfo
                {
                    Id = dev.ID,
                    Name = name,
                    IsDefault = isDefault,
                    IsVirtualCable = isVirtual,
                    Flow = "Render"
                });
            }

            return list;
        }

        public List<AudioDeviceInfo> GetCaptureDevices()
        {
            var list = new List<AudioDeviceInfo>();
            MMDevice? defaultDevice = null;

            try
            {
                defaultDevice = _enumerator.GetDefaultAudioEndpoint(DataFlow.Capture, Role.Communications);
            }
            catch { }

            var devices = _enumerator.EnumerateAudioEndPoints(DataFlow.Capture, DeviceState.Active);
            foreach (var dev in devices)
            {
                var name = dev.FriendlyName;
                var isVirtual = IsVirtualAudioDevice(name);
                var isDefault = defaultDevice != null && dev.ID == defaultDevice.ID;

                list.Add(new AudioDeviceInfo
                {
                    Id = dev.ID,
                    Name = name,
                    IsDefault = isDefault,
                    IsVirtualCable = isVirtual,
                    Flow = "Capture"
                });
            }

            return list;
        }

        public MMDevice? GetDeviceById(string id, DataFlow flow)
        {
            if (!string.IsNullOrWhiteSpace(id))
            {
                try
                {
                    var dev = _enumerator.GetDevice(id);
                    if (dev != null && dev.State == DeviceState.Active)
                    {
                        return dev;
                    }
                }
                catch { }
            }

            // Fallback to system default endpoint
            try
            {
                var role = flow == DataFlow.Capture ? Role.Communications : Role.Multimedia;
                var def = _enumerator.GetDefaultAudioEndpoint(flow, role);
                if (def != null && def.State == DeviceState.Active)
                {
                    return def;
                }
            }
            catch { }

            // Fallback to first active device of this flow
            try
            {
                return _enumerator.EnumerateAudioEndPoints(flow, DeviceState.Active).FirstOrDefault();
            }
            catch
            {
                return null;
            }
        }

        public bool IsVirtualCableInstalled()
        {
            var playback = GetPlaybackDevices();
            return playback.Any(d => d.IsVirtualCable);
        }

        private static bool IsVirtualAudioDevice(string name)
        {
            var lower = name.ToLowerInvariant();
            return lower.Contains("cable") ||
                   lower.Contains("vb-audio") ||
                   lower.Contains("virtual") ||
                   lower.Contains("voicemeeter") ||
                   lower.Contains("vac");
        }
    }
}
