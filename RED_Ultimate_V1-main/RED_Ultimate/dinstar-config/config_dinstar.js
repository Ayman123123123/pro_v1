const puppeteer = require('puppeteer');
const fs = require('fs');

(async () => {
  const browser = await puppeteer.launch({
    headless: "new",
    ignoreHTTPSErrors: true,
    executablePath: "C:\\Users\\hpc01\\.cache\\puppeteer\\chrome\\win64-152.0.7977.42\\chrome-win64\\chrome.exe",
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  const page = await browser.newPage();
  
  try {
    console.log("Navigating to DINSTAR login page...");
    await page.goto('https://192.168.11.1/enLogin.htm', { waitUntil: 'networkidle2' });
    
    console.log("Entering credentials...");
    await page.type('input[type="text"]', 'admin'); 
    await page.type('input[type="password"]', 'admin');
    
    // Evaluate in page to click the submit button
    await page.evaluate(() => {
        let btn = document.querySelector('input[type="button"]') || document.querySelector('input[type="submit"]') || document.querySelector('.loginBtn');
        if (btn) btn.click();
    });

    await page.waitForNavigation({ waitUntil: 'networkidle2' });
    
    console.log("Logged in successfully. Taking screenshot...");
    await page.screenshot({ path: 'dinstar_dashboard.png' });
    
    const links = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('a')).map(a => ({ text: a.innerText, href: a.href }));
    });
    
    const frames = await page.frames();
    console.log(`Found ${frames.length} frames.`);
    for (let i = 0; i < frames.length; i++) {
        console.log(`Frame ${i} URL: ${frames[i].url()}`);
    }

    fs.writeFileSync('dinstar_links.json', JSON.stringify(links, null, 2));
    console.log("Links saved to dinstar_links.json");

  } catch (error) {
    console.error("Error occurred:", error);
    await page.screenshot({ path: 'error_screenshot.png' });
  } finally {
    await browser.close();
  }
})();
