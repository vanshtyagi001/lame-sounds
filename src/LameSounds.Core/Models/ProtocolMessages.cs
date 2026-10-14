using System.Text.Json.Serialization;

namespace LameSounds.Core.Models
{
    public class UdpTriggerPacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "play"; // "play", "stop", "stop_all"

        [JsonPropertyName("soundId")]
        public string SoundId { get; set; } = string.Empty;

        [JsonPropertyName("volume")]
        public float? Volume { get; set; }

        [JsonPropertyName("speakerVolume")]
        public float? SpeakerVolume { get; set; }

        [JsonPropertyName("micVolume")]
        public float? MicVolume { get; set; }
    }

    public class WebSocketMessage
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = string.Empty;

        [JsonPropertyName("payload")]
        public object? Payload { get; set; }
    }

    public class AudioDeviceInfo
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = string.Empty;

        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;

        [JsonPropertyName("isDefault")]
        public bool IsDefault { get; set; }

        [JsonPropertyName("isVirtualCable")]
        public bool IsVirtualCable { get; set; }

        [JsonPropertyName("flow")]
        public string Flow { get; set; } = "Render"; // "Render" or "Capture"

        public override string ToString() => Name;
    }
}
