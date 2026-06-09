const formatISTTime = (dateString) => {
  try {
    // Backend sends IST timestamps like: 2026-02-13T16:58:10.033921+05:30
    // Parse the timestamp directly without timezone conversion
    const match = dateString.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
    if (!match) {
      return dateString;
    }
    
    const [_, year, month, day, hour24, minute, second] = match;
    let hour = parseInt(hour24);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12 || 12; // Convert to 12-hour format
    
    return `${day}/${month}/${year} ${String(hour).padStart(2, '0')}:${minute}:${second} ${ampm} IST`;
  } catch (e) {
    return dateString;
  }
};

console.log('='.repeat(70));
console.log('TIMESTAMP FORMATTING TEST');
console.log('='.repeat(70));
console.log('');
console.log('Backend sends: 2026-02-14T09:08:59.181237+05:30');
console.log('Dashboard shows:', formatISTTime('2026-02-14T09:08:59.181237+05:30'));
console.log('');
console.log('Expected result: 14/02/2026 09:08:59 AM IST');
console.log('');
console.log('Test 2:');
console.log('Backend sends: 2026-02-14T21:30:45.123456+05:30');
console.log('Dashboard shows:', formatISTTime('2026-02-14T21:30:45.123456+05:30'));
console.log('Expected result: 14/02/2026 09:30:45 PM IST');
console.log('');
console.log('='.repeat(70));
