package dev.orderproof.emberkitchen.core

import com.google.common.truth.Truth.assertThat
import dev.orderproof.emberkitchen.data.settings.ConnectionSettings
import org.junit.Test

class ConnectionSettingsTest {
    @Test
    fun normalizesTypedAddresses() {
        assertThat(ConnectionSettings.normalize(" 192.168.1.20:8787/ ")).isEqualTo("http://192.168.1.20:8787")
        assertThat(ConnectionSettings.normalize("http://10.0.2.2:8787")).isEqualTo("http://10.0.2.2:8787")
        assertThat(ConnectionSettings.normalize("https://kitchen.test")).isEqualTo("https://kitchen.test")
    }

    @Test
    fun rejectsNonHttpOrHostlessAddresses() {
        assertThat(ConnectionSettings.normalize("")).isNull()
        assertThat(ConnectionSettings.normalize("ftp://host:21")).isNull()
        assertThat(ConnectionSettings.normalize("http://")).isNull()
        assertThat(ConnectionSettings.normalize("not a url")).isNull()
    }
}
