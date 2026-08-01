package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object GithubUserParser {

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun parseUserJson(fileContent: String, filename: String, sha: String): GithubUser? {
        return try {
            val root = JSONObject(fileContent)
            val id: String
            val userObj: JSONObject

            if (root.has("status") || root.has("nome") || root.has("numero") || root.has("senha_hash") || root.has("senha") || root.has("password")) {
                userObj = root
                id = filename.removeSuffix(".json")
            } else {
                val keys = root.keys()
                if (!keys.hasNext()) return null
                id = keys.next()
                userObj = root.getJSONObject(id)
            }

            val status = userObj.optString("status", "ATIVO")
            val origem = userObj.optString("origem", "sms_fimaster")
            val numero = userObj.optString("numero", "")
            val nome = userObj.optString("nome", "")
            val idTransacao = userObj.optString("id_transacao", "")
            val saldo = userObj.optDouble("saldo", 0.0)
            val senhaHash = userObj.optString("senha_hash", userObj.optString("senha", userObj.optString("password", userObj.optString("pass", ""))))
            val salt = userObj.optString("salt", userObj.optString("sal", ""))
            val tokenRecuperacao = userObj.optString("token_recuperacao", "")
            val nivelAutorizacao = userObj.optString("nivel_autorizacao", "CLIENTE")
            val dataRegistro = userObj.optString("data_registro", "")
            val ultimaAtualizacao = userObj.optString("ultima_atualizacao", "")

            val mt5Obj = userObj.optJSONObject("mt5")
            val mt5Registrado = mt5Obj?.optBoolean("registrado") ?: false
            val mt5IdConta = mt5Obj?.optString("id_conta") ?: ""

            val licencaObj = userObj.optJSONObject("licenca")
            val licencaAtiva = licencaObj?.optBoolean("ativa") ?: false
            val licencaProduto = licencaObj?.optString("produto") ?: "Fimaster"
            val licencaPlano = licencaObj?.optString("plano") ?: "Anual"
            val licencaValidade = licencaObj?.optString("validade", "") ?: ""
            val licencaUltimaRenovacao = licencaObj?.optString("ultima_renovacao", "") ?: ""
            val licencaTotalRenovacoes = licencaObj?.optInt("total_renovacoes", 0) ?: 0

            val licencaHistorico = mutableListOf<GithubUserHistorico>()
            val histArray = licencaObj?.optJSONArray("historico")
            if (histArray != null) {
                for (i in 0 until histArray.length()) {
                    val h = histArray.getJSONObject(i)
                    licencaHistorico.add(
                        GithubUserHistorico(
                            data = h.optString("data", ""),
                            valor = h.optDouble("valor", 0.0),
                            descricao = h.optString("descricao", "")
                        )
                    )
                }
            }

            val reembolsoObj = userObj.optJSONObject("reembolso")
            val reembolsoSolicitado = reembolsoObj?.optBoolean("solicitado") ?: false
            val reembolsoStatus = reembolsoObj?.optString("status", "NENHUM") ?: "NENHUM"

            val autorizacaoObj = userObj.optJSONObject("autorizacao")
            val autorizacaoStatus = autorizacaoObj?.optString("status", "") ?: ""
            val autorizacaoAprovadoPor = autorizacaoObj?.optString("aprovado_por", "") ?: ""
            val autorizacaoDataAprovacao = autorizacaoObj?.optString("data_aprovacao", "") ?: ""

            val auditoriaObj = userObj.optJSONObject("auditoria")
            val auditoriaUltimoLogin = auditoriaObj?.optString("ultimo_login", "") ?: ""
            val auditoriaUltimoDispositivo = auditoriaObj?.optString("ultimo_dispositivo", "") ?: ""
            val auditoriaTentativasLogin = auditoriaObj?.optInt("tentativas_login", 0) ?: 0

            val creditoGuardado = userObj.optDouble("credito_guardado", 0.0)

            GithubUser(
                id = id,
                status = status,
                origem = origem,
                numero = numero,
                nome = nome,
                idTransacao = idTransacao,
                saldo = saldo,
                senhaHash = senhaHash,
                salt = salt,
                tokenRecuperacao = tokenRecuperacao,
                nivelAutorizacao = nivelAutorizacao,
                dataRegistro = dataRegistro,
                ultimaAtualizacao = ultimaAtualizacao,
                mt5Registrado = mt5Registrado,
                mt5IdConta = mt5IdConta,
                licencaAtiva = licencaAtiva,
                licencaProduto = licencaProduto,
                licencaPlano = licencaPlano,
                licencaValidade = licencaValidade,
                licencaUltimaRenovacao = licencaUltimaRenovacao,
                licencaTotalRenovacoes = licencaTotalRenovacoes,
                licencaHistorico = licencaHistorico,
                reembolsoSolicitado = reembolsoSolicitado,
                reembolsoStatus = reembolsoStatus,
                autorizacaoStatus = autorizacaoStatus,
                autorizacaoAprovadoPor = autorizacaoAprovadoPor,
                autorizacaoDataAprovacao = autorizacaoDataAprovacao,
                creditoGuardado = creditoGuardado,
                auditoriaUltimoLogin = auditoriaUltimoLogin,
                auditoriaUltimoDispositivo = auditoriaUltimoDispositivo,
                auditoriaTentativasLogin = auditoriaTentativasLogin,
                sha = sha,
                filename = filename
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun serializeUserJson(user: GithubUser, wrapWithId: Boolean = true): String {
        val root = JSONObject()
        val userObj = JSONObject()

        userObj.put("status", user.status)
        userObj.put("origem", user.origem)
        userObj.put("numero", user.numero)
        userObj.put("nome", user.nome)
        userObj.put("id_transacao", user.idTransacao)
        userObj.put("saldo", user.saldo)
        userObj.put("senha_hash", user.senhaHash)
        userObj.put("salt", user.salt)
        userObj.put("token_recuperacao", user.tokenRecuperacao)
        userObj.put("nivel_autorizacao", user.nivelAutorizacao)
        userObj.put("data_registro", user.dataRegistro)
        userObj.put("ultima_atualizacao", user.ultimaAtualizacao)

        val mt5 = JSONObject().apply {
            put("registrado", user.mt5Registrado)
            put("id_conta", user.mt5IdConta)
        }
        userObj.put("mt5", mt5)

        val licenca = JSONObject().apply {
            put("ativa", user.licencaAtiva)
            put("produto", user.licencaProduto)
            put("plano", user.licencaPlano)
            put("validade", user.licencaValidade)
            put("ultima_renovacao", user.licencaUltimaRenovacao)
            put("total_renovacoes", user.licencaTotalRenovacoes)
            
            val histArr = JSONArray()
            user.licencaHistorico.forEach { h ->
                histArr.put(JSONObject().apply {
                    put("data", h.data)
                    put("valor", h.valor)
                    put("descricao", h.descricao)
                })
            }
            put("historico", histArr)
        }
        userObj.put("licenca", licenca)

        val reembolso = JSONObject().apply {
            put("solicitado", user.reembolsoSolicitado)
            put("status", user.reembolsoStatus)
        }
        userObj.put("reembolso", reembolso)

        val autorizacao = JSONObject().apply {
            put("status", user.autorizacaoStatus)
            put("aprovado_por", user.autorizacaoAprovadoPor)
            put("data_aprovacao", user.autorizacaoDataAprovacao)
        }
        userObj.put("autorizacao", autorizacao)

        val auditoria = JSONObject().apply {
            put("ultimo_login", user.auditoriaUltimoLogin)
            put("ultimo_dispositivo", user.auditoriaUltimoDispositivo)
            put("tentativas_login", user.auditoriaTentativasLogin)
        }
        userObj.put("auditoria", auditoria)

        userObj.put("credito_guardado", user.creditoGuardado)

        return if (wrapWithId) {
            root.put(user.id, userObj)
            root.toString(2)
        } else {
            userObj.toString(2)
        }
    }

    // Generates a random alphanumeric salt of standard 8 bytes (16 hex chars)
    fun generateSalt(): String {
        val allowedChars = "0123456789abcdef"
        return (1..16)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
