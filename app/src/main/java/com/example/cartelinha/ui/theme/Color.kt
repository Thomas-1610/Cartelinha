package com.example.cartelinha.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta Cartelinha — base rosa pêssego (#EFA6A5).
 *
 * Hierarquia de uso (60-30-10):
 *  - 60% Canvas  → Sand100 (fundo das telas)
 *  - 30% Estrutura → Sand50 / Peach50 (cards, barras)
 *  - 10% Ação    → Peach600/700 (botões, links, foco, número em destaque)
 *
 * Importância visual:
 *  - ALTA   → Peach600 (CTA), Peach700 (texto de destaque), Peach800 (títulos em cards peach)
 *  - MÉDIA  → Peach300/400 (estados ativos, dias de uso, ícones)
 *  - BAIXA  → Peach50/100/200 (fundos de destaque, trilhas, chips neutros)
 */

// ---------- Pêssego (tonal 50 → 900) ----------
val Peach50 = Color(0xFFFFF6F4)
val Peach100 = Color(0xFFFCEAE7)
val Peach200 = Color(0xFFF8D8D5)
val Peach300 = Color(0xFFF4C0BE)
val Peach400 = Color(0xFFEFA6A5) // cor da marca
val Peach500 = Color(0xFFE08F8E)
val Peach600 = Color(0xFFBC6261) // CTA — contraste ~4.2:1 com branco
val Peach700 = Color(0xFF9E4948) // texto de destaque sobre claro (5.6:1)
val Peach800 = Color(0xFF7A3534)
val Peach900 = Color(0xFF552423)

// ---------- Damasco (secundária quente, 50 → 700) ----------
val Apricot50 = Color(0xFFFEF5F0)
val Apricot100 = Color(0xFFFDEBE3)
val Apricot300 = Color(0xFFF4C5B5)
val Apricot500 = Color(0xFFE39E85)
val Apricot700 = Color(0xFFA9634A)

// ---------- Neutros quentes (areia) ----------
val Sand50 = Color(0xFFFFFDFC)   // superfície elevada (cards)
val Sand100 = Color(0xFFFAF7F2)  // canvas
val Sand200 = Color(0xFFF3EDE6)  // superfície rebaixada / trilhas
val Sand300 = Color(0xFFE9E0D8)  // bordas sutis / divisores
val Sand400 = Color(0xFFD6C9C0)  // bordas de campos

// ---------- Texto (título → corpo → secundário → desabilitado) ----------
val TextTitle = Color(0xFF332827)
val TextPrimary = Color(0xFF4A3B3B)
val TextSecondary = Color(0xFF7A6868) // 4.9:1 no canvas
val TextMuted = Color(0xFFB3A4A4)     // apenas desabilitado / decorativo

// ---------- Funcionais (fundo suave / sólido / texto) ----------
val SuccessGreenSoft = Color(0xFFE3F0E4)
val SuccessGreen = Color(0xFF7FB685)
val SuccessGreenText = Color(0xFF3F7A47)
val WarningYellowSoft = Color(0xFFFBF0D9)
val WarningYellow = Color(0xFFE8C468)
val WarningYellowText = Color(0xFF8A6414)
val AlertRedSoft = Color(0xFFF9E4E4)
val AlertRed = Color(0xFFD97B7B)
val AlertRedText = Color(0xFFA84444)

// ---------- Aliases legados (mantidos para compatibilidade) ----------
val PeachPrimary = Peach400
val PeachStrong = Peach600
val PeachDeep = Peach700
val PeachPrimaryDark = Peach700
val PeachLight = Peach200
val PeachSoft = Peach100
val PeachAccent = Apricot300
val CreamBg = Sand100
val WhitePastel = Sand50
