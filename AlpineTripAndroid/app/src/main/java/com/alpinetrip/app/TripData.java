package com.alpinetrip.app;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TripData {
    public static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    public static final class Point {
        public final String name;
        public final double lat;
        public final double lon;
        public Point(String name, double lat, double lon) {
            this.name = name; this.lat = lat; this.lon = lon;
        }
    }

    public static final class Day {
        public final LocalDate date;
        public final String title;
        public final List<Point> weatherPoints;
        public final List<String> roadKeywords;
        public Day(String date, String title, List<Point> weatherPoints, List<String> roadKeywords) {
            this.date = LocalDate.parse(date);
            this.title = title;
            this.weatherPoints = weatherPoints;
            this.roadKeywords = roadKeywords;
        }
    }

    public static final List<Day> DAYS = Arrays.asList(
        new Day("2026-09-17", "Malpensa → Morcote → Lugano",
            Arrays.asList(new Point("Morcote",45.9266,8.9167), new Point("Lugano",46.0037,8.9511)),
            Arrays.asList("morcote","lugano","paradiso","melide")),
        new Day("2026-09-18", "Lugano → San Bernardino → Viamala → Julier → Pontresina",
            Arrays.asList(new Point("San Bernardino",46.497,9.171), new Point("Julier",46.471,9.727), new Point("Pontresina",46.4908,9.9020)),
            Arrays.asList("san bernardino","san-bernardino","passo del san bernardino","hinterrhein","thusis","viamala","julier","julierpass","güglia","pontresina")),
        new Day("2026-09-19", "Pontresina / Bernina / Morteratsch",
            Arrays.asList(new Point("Diavolezza",46.441,9.982), new Point("Bernina",46.410,10.020), new Point("Pontresina",46.4908,9.9020)),
            Arrays.asList("bernina","berninapass","passo del bernina","pontresina","morteratsch","diavolezza")),
        new Day("2026-09-20", "Pontresina → Albula → Landwasser → Pradaschier → Andermatt",
            Arrays.asList(new Point("Albula",46.583,9.838), new Point("Andermatt",46.6356,8.5939)),
            Arrays.asList("albula","albulapass","alvra","filisur","landwasser","churwalden","pradaschier","andermatt","schöllenen","schoellenen")),
        new Day("2026-09-21", "Andermatt → Susten → Grimsel → Furka → Gotthard/Tremola → Ascona",
            Arrays.asList(new Point("Susten",46.729,8.449), new Point("Grimsel",46.562,8.337), new Point("Furka",46.572,8.415), new Point("Gotthard",46.556,8.561)),
            Arrays.asList("susten","sustenpass","wassen","innertkirchen","grimsel","grimselpass","gletsch","furka","furkapass","realp","hospental","gotthard","gotthardpass","san gottardo","tremola","airolo")),
        new Day("2026-09-22", "Ascona → Verzasca → Lavertezzo → Malpensa",
            Arrays.asList(new Point("Verzasca",46.223,8.857), new Point("Ascona",46.1547,8.7733)),
            Arrays.asList("ascona","tenero","verzasca","lavertezzo","locarno"))
    );

    private TripData() {}

    public static int dayIndexFor(LocalDate date) {
        for (int i=0; i<DAYS.size(); i++) if (DAYS.get(i).date.equals(date)) return i;
        return -1;
    }
}
