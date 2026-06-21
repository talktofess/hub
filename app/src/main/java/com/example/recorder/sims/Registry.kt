package com.example.recorder.sims

import com.example.recorder.sims.claude.ClaudeSim
import com.example.recorder.sims.email.EmailSim
import com.example.recorder.sims.imessage.IMessageSim
import com.example.recorder.sims.lists.ListsSim
import com.example.recorder.sims.notes.NotesSim
import com.example.recorder.sims.typer.TyperSim
import com.example.recorder.sims.typewriter.TypewriterSim
import com.example.recorder.sims.whatsapp.WhatsAppSim

/**
 * The sim registry. Port of src/sims/registry.ts. (Corporate folded into Notes
 * as a Document type; TikTok skipped. Remaining: journal.)
 */
val SIMS: List<SimDef> = listOf(
    NotesSim,
    IMessageSim,
    WhatsAppSim,
    EmailSim,
    ListsSim,
    TyperSim,
    TypewriterSim,
    ClaudeSim,
)

fun getSim(id: String?): SimDef? = SIMS.firstOrNull { it.id == id }

fun defaultSim(): SimDef = SIMS.firstOrNull { it.ready } ?: SIMS.first()
