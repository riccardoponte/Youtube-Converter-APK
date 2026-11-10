const fs = require('fs');
const path = require('path');
const ffmpeg = require('fluent-ffmpeg');

// Imposta il percorso del binario ffmpeg se presente nel sistema
if (fs.existsSync('/usr/bin/ffmpeg')) {
  ffmpeg.setFfmpegPath('/usr/bin/ffmpeg');
}

// Supporto per @distube/ytdl-core (v5+ classe Ytdl o v4+ funzione) e fallback su ytdl-core
let ytdlModule;
try {
  ytdlModule = require('@distube/ytdl-core');
} catch {
  ytdlModule = require('ytdl-core');
}

let ytdlInstance = null;
if (ytdlModule && ytdlModule.Ytdl) {
  ytdlInstance = new ytdlModule.Ytdl();
  // Se esiste un file cookies.txt o cookies.json, caricalo per aggirare il bot-check di YouTube
  if (fs.existsSync(path.join(__dirname, 'cookies.txt'))) {
    try {
      const cookieContent = fs.readFileSync(path.join(__dirname, 'cookies.txt'), 'utf8');
      ytdlInstance.setCookie(cookieContent);
      console.log('Cookie di autenticazione caricati da cookies.txt');
    } catch (e) {
      console.warn('Avviso: impossibile leggere cookies.txt:', e.message);
    }
  }
}

// Wrapper per sintassi standard ytdl(videoURL, options)
function ytdl(url, options = {}) {
  if (ytdlInstance) {
    return ytdlInstance.download(url, options);
  }
  if (typeof ytdlModule === 'function') {
    return ytdlModule(url, options);
  }
  if (ytdlModule.default && typeof ytdlModule.default === 'function') {
    return ytdlModule.default(url, options);
  }
  throw new Error('Nessun motore ytdl valido trovato.');
}

async function getInfo(url) {
  if (ytdlInstance) {
    return await ytdlInstance.getInfo(url);
  }
  if (ytdlModule.getInfo) {
    return await ytdlModule.getInfo(url);
  }
  throw new Error('Metodo getInfo non disponibile.');
}

// Parametri di input da riga di comando o default
const videoURL = process.argv[2] || 'https://www.youtube.com/watch?v=rBjjbr31hZM';
const format = (process.argv[3] || 'mp4').toLowerCase(); // 'mp4' o 'mp3'
const outputPath = process.argv[4] || (format === 'mp3' ? 'audio.mp3' : 'video.mp4');

console.log('====================================');
console.log(' TubeConvert - YouTube Downloader CLI');
console.log('====================================');
console.log(`URL Video:     ${videoURL}`);
console.log(`Formato:       ${format.toUpperCase()}`);
console.log(`File di output: ${outputPath}`);
console.log('------------------------------------');

async function main() {
  try {
    console.log('Recupero informazioni sul video in corso...');
    const info = await getInfo(videoURL);
    const title = info.title || (info.videoDetails && info.videoDetails.title) || 'Video YouTube';
    const author = (info.author && info.author.name) || (info.videoDetails && info.videoDetails.author && info.videoDetails.author.name) || 'YouTube Creator';
    
    console.log(`Titolo:  ${title}`);
    console.log(`Canale:  ${author}`);
    console.log('------------------------------------');

    if (format === 'mp3') {
      console.log('Avvio conversione e download in formato MP3 (320 kbps)...');
      const audioStream = ytdl(videoURL, {
        filter: 'audioonly',
        quality: 'highestaudio'
      });

      audioStream.on('error', handleError);

      ffmpeg(audioStream)
        .audioBitrate(320)
        .format('mp3')
        .save(outputPath)
        .on('progress', p => {
          if (p.percent) {
            process.stdout.write(`\rAvanzamento conversione: ${Math.round(p.percent)}%`);
          }
        })
        .on('end', () => {
          console.log('\nDownload e conversione MP3 completati con successo!');
          console.log(`File salvato in: ${path.resolve(outputPath)}`);
        })
        .on('error', err => {
          console.error('\nErrore ffmpeg:', err.message);
        });

    } else {
      console.log('Avvio download in formato MP4...');
      const stream = ytdl(videoURL, {
        quality: 'highest'
      });

      let downloadedBytes = 0;
      stream.on('data', chunk => {
        downloadedBytes += chunk.length;
        const mb = (downloadedBytes / (1024 * 1024)).toFixed(2);
        process.stdout.write(`\rScaricati: ${mb} MB`);
      });

      stream.on('error', handleError);

      stream.pipe(fs.createWriteStream(outputPath))
        .on('finish', () => {
          console.log('\nDownload completato!');
          console.log(`File salvato in: ${path.resolve(outputPath)}`);
        })
        .on('error', err => {
          console.error('\nErrore scrittura file:', err.message);
        });
    }

  } catch (error) {
    handleError(error);
  }
}

function handleError(err) {
  console.error('\n[ERRORE DURANTE IL DOWNLOAD]');
  console.error(err.message || err);

  if (err.message && (err.message.includes('bot') || err.message.includes('BOT_CHECK') || err.message.includes('410'))) {
    console.log('\nNOTA SUGLI INDIRIZZI IP DATACENTER / BOT-CHECK YOUTUBE:');
    console.log('YouTube richiede l\'autenticazione tramite cookie per prevenire il download automatico da cloud.');
    console.log('Per risolvere:');
    console.log('1. Esporta i tuoi cookie di YouTube tramite un\'estensione (es. "Get cookies.txt LOCALLY").');
    console.log('2. Salva il file nella stessa cartella come "cookies.txt".');
    console.log('3. Esegui nuovamente: node index.js <URL>');
  }
}

main();
