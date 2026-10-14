using System.Text.Json.Serialization;

namespace LameSounds.Core.Models
{
    public class AudioConfig
    {
        [JsonPropertyName("monitorDeviceId")]
        public string MonitorDeviceId { get; set; } = string.Empty;

        [JsonPropertyName("broadcastDeviceId")]
        public string BroadcastDeviceId { get; set; } = string.Empty;

        [JsonPropertyName("micDeviceId")]
        public string MicDeviceId { get; set; } = string.Empty;

        [JsonPropertyName("micPassthroughEnabled")]
        public bool MicPassthroughEnabled { get; set; } = false;

        [JsonPropertyName("autoDuckingEnabled")]
        public bool AutoDuckingEnabled { get; set; } = true;

        [JsonPropertyName("duckingReductionPercent")]
        public float DuckingReductionPercent { get; set; } = 0.5f;

        [JsonPropertyName("monitorVolume")]
        public float MonitorVolume { get; set; } = 1.0f;

        [JsonPropertyName("broadcastVolume")]
        public float BroadcastVolume { get; set; } = 1.0f;

        [JsonPropertyName("micVolume")]
        public float MicVolume { get; set; } = 1.0f;

        [JsonPropertyName("bufferLatencyMs")]
        public int BufferLatencyMs { get; set; } = 40;

        [JsonPropertyName("udpPort")]
        public int UdpPort { get; set; } = 50051;

        [JsonPropertyName("httpPort")]
        public int HttpPort { get; set; } = 50050;
    }
}
