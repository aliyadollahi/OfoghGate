package com.example

import com.example.data.model.KnockProtocol
import com.example.data.model.KnockStep
import com.example.data.model.NetworkMode
import com.example.data.model.SoftphoneConfig
import com.example.data.model.TransportType
import com.example.data.provisioning.MikroTikScriptGenerator
import com.example.data.provisioning.ProvisionDecodeResult
import com.example.data.provisioning.ProvisioningCodec
import com.example.data.provisioning.RouterOsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningAndMikroTikTest {

    private val sampleConfig = SoftphoneConfig(
        extension = "105",
        authUser = "105",
        password = "Secret#SipPassword",
        displayName = "Ali Tech",
        lanHost = "192.168.10.20",
        lanPort = 5060,
        wanHost = "pbx.example.ir",
        wanPort = 5160,
        transport = TransportType.TCP,
        networkMode = NetworkMode.AUTO_LAN_WAN,
        knockSteps = listOf(
            KnockStep(11001, KnockProtocol.UDP, 150),
            KnockStep(22002, KnockProtocol.TCP, 200),
            KnockStep(33003, KnockProtocol.UDP, 150)
        ),
        knockIntervalMinutes = 15
    )

    @Test
    fun plainProvisioningRoundTrip() {
        val encoded = ProvisioningCodec.encodePlain(sampleConfig)
        assertTrue(encoded.startsWith("ISSABEL-GATE://v1.plain."))

        val decoded = ProvisioningCodec.decode(encoded)
        assertTrue(decoded is ProvisionDecodeResult.Success)
        assertEquals(sampleConfig, (decoded as ProvisionDecodeResult.Success).config)
    }

    @Test
    fun encryptedProvisioningRoundTripAndPinRejection() {
        val pin = "9482"
        val encoded = ProvisioningCodec.encodeEncrypted(sampleConfig, pin)
        assertTrue(encoded.startsWith("ISSABEL-GATE://v1.enc."))

        assertTrue(ProvisioningCodec.decode(encoded, null) is ProvisionDecodeResult.PinRequired)
        assertTrue(ProvisioningCodec.decode(encoded, "0000") is ProvisionDecodeResult.WrongPinOrCorrupted)

        val decoded = ProvisioningCodec.decode(encoded, pin)
        assertTrue(decoded is ProvisionDecodeResult.Success)
        assertEquals(sampleConfig, (decoded as ProvisionDecodeResult.Success).config)
    }

    @Test
    fun mikrotikScriptContainsSipAlgDisableAndKnockStages() {
        val scriptV7 = MikroTikScriptGenerator.generate(sampleConfig, RouterOsVersion.V7)
        assertTrue(scriptV7.contains("/ip firewall service-port set sip disabled=yes"))
        assertTrue(scriptV7.contains("issabel_knock_stage_1"))
        assertTrue(scriptV7.contains("issabel_knock_stage_2"))
        assertTrue(scriptV7.contains("address-list=issabel_allowed"))
        assertTrue(scriptV7.contains("address-list-timeout=00:20:00"))
        assertTrue(scriptV7.contains("dst-port=10000-20000"))
        assertTrue(scriptV7.contains("to-addresses=192.168.10.20"))
        assertTrue(scriptV7.contains("dst-port=5160"))
    }
}
