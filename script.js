const copyBtn = document.getElementById('copyBtn');
const sqlCode = document.getElementById('sqlCode');

copyBtn.addEventListener('click', () => {
  navigator.clipboard.writeText(sqlCode.textContent).then(() => {
    copyBtn.textContent = 'Copiado';
    copyBtn.classList.add('copied');
    window.setTimeout(() => {
      copyBtn.textContent = 'Copiar';
      copyBtn.classList.remove('copied');
    }, 1800);
  });
});