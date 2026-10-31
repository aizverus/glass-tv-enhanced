package com.gathin.tvhome;

import java.util.Calendar;

/**
 * Short Quran verses and sahih hadith that encourage, shown on the shelf above the dock in place of
 * the picture's name when a theme sets {@code "hero_quotes": true}. Each is the Arabic, where it comes
 * from, and its meaning in Malay. Kept short so the Arabic fits one line.
 */
final class Quotes {
    /** {Arabic, source, Malay meaning}. */
    static final String[][] ALL = {
            {"فَإِنَّ مَعَ الْعُسْرِ يُسْرًا", "Surah Al-Insyirah 94:5",
                    "Maka sesungguhnya bersama kesulitan itu ada kemudahan."},
            {"لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا", "Surah Al-Baqarah 2:286",
                    "Allah tidak membebani seseorang melainkan sesuai dengan kesanggupannya."},
            {"أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ", "Surah Ar-Ra'd 13:28",
                    "Ingatlah, dengan mengingati Allah, hati menjadi tenang."},
            {"إِنَّ اللَّهَ مَعَ الصَّابِرِينَ", "Surah Al-Baqarah 2:153",
                    "Sesungguhnya Allah bersama orang-orang yang sabar."},
            {"لَا تَقْنَطُوا مِن رَّحْمَةِ اللَّهِ", "Surah Az-Zumar 39:53",
                    "Janganlah kamu berputus asa daripada rahmat Allah."},
            {"وَلَا تَهِنُوا وَلَا تَحْزَنُوا", "Surah Ali 'Imran 3:139",
                    "Janganlah kamu bersifat lemah dan janganlah kamu bersedih hati."},
            {"وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ", "Surah At-Talaq 65:3",
                    "Sesiapa yang bertawakal kepada Allah, maka Allah mencukupkan keperluannya."},
            {"لَئِن شَكَرْتُمْ لَأَزِيدَنَّكُمْ", "Surah Ibrahim 14:7",
                    "Sesungguhnya jika kamu bersyukur, nescaya Aku akan menambahkan nikmat-Ku kepadamu."},
            {"فَاذْكُرُونِي أَذْكُرْكُمْ", "Surah Al-Baqarah 2:152",
                    "Maka ingatlah kamu kepada-Ku, nescaya Aku ingat kepadamu."},
            {"لَا تَحْزَنْ إِنَّ اللَّهَ مَعَنَا", "Surah At-Taubah 9:40",
                    "Janganlah engkau berdukacita, sesungguhnya Allah bersama kita."},
            {"رَّبِّ زِدْنِي عِلْمًا", "Surah Taha 20:114",
                    "Ya Tuhanku, tambahkanlah ilmu kepadaku."},
            {"ادْعُونِي أَسْتَجِبْ لَكُمْ", "Surah Ghafir 40:60",
                    "Berdoalah kepada-Ku, nescaya Aku perkenankan doa kamu."},
            {"وَاسْتَعِينُوا بِالصَّبْرِ وَالصَّلَاةِ", "Surah Al-Baqarah 2:45",
                    "Dan mintalah pertolongan dengan sabar dan solat."},
            {"وَأَن لَّيْسَ لِلْإِنسَانِ إِلَّا مَا سَعَىٰ", "Surah An-Najm 53:39",
                    "Dan bahawa manusia tidak memperoleh selain apa yang telah diusahakannya."},
            {"فَإِذَا عَزَمْتَ فَتَوَكَّلْ عَلَى اللَّهِ", "Surah Ali 'Imran 3:159",
                    "Apabila engkau telah berazam, maka bertawakallah kepada Allah."},
            {"وَلَسَوْفَ يُعْطِيكَ رَبُّكَ فَتَرْضَىٰ", "Surah Ad-Duha 93:5",
                    "Dan sesungguhnya Tuhanmu akan memberikan kurnia-Nya kepadamu, lalu engkau reda."},
            {"إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ", "Hadis riwayat al-Bukhari dan Muslim",
                    "Sesungguhnya setiap amalan itu bergantung kepada niat."},
            {"الْكَلِمَةُ الطَّيِّبَةُ صَدَقَةٌ", "Hadis riwayat al-Bukhari dan Muslim",
                    "Kata-kata yang baik itu adalah sedekah."},
            {"تَبَسُّمُكَ فِي وَجْهِ أَخِيكَ لَكَ صَدَقَةٌ", "Hadis riwayat at-Tirmizi",
                    "Senyumanmu di hadapan saudaramu adalah sedekah bagimu."},
            {"أَحَبُّ الْأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ", "Hadis riwayat al-Bukhari dan Muslim",
                    "Amalan yang paling dicintai Allah ialah yang berterusan walaupun sedikit."},
            {"احْرِصْ عَلَى مَا يَنْفَعُكَ وَاسْتَعِنْ بِاللَّهِ وَلَا تَعْجَزْ", "Hadis riwayat Muslim",
                    "Bersungguh-sungguhlah pada perkara yang memberi manfaat kepadamu, mohonlah pertolongan Allah dan jangan lemah."},
            {"لَا تَغْضَبْ", "Hadis riwayat al-Bukhari",
                    "Janganlah engkau marah."},
            {"الطُّهُورُ شَطْرُ الْإِيمَانِ", "Hadis riwayat Muslim",
                    "Kebersihan itu sebahagian daripada iman."},
            {"إِنَّ اللَّهَ رَفِيقٌ يُحِبُّ الرِّفْقَ", "Hadis riwayat al-Bukhari dan Muslim",
                    "Sesungguhnya Allah Maha Lembut dan menyukai kelembutan."},
            {"الدِّينُ النَّصِيحَةُ", "Hadis riwayat Muslim",
                    "Agama itu adalah nasihat."},
    };

    private Quotes() {}

    /** A quote for this picture today: it changes with every picture, and the same picture says something else tomorrow. */
    static String[] forPicture(String pictureName, Calendar day) {
        int seed = (pictureName == null ? 0 : pictureName.hashCode()) + day.get(Calendar.DAY_OF_YEAR) * 7;
        return ALL[Math.floorMod(seed, ALL.length)];
    }
}
