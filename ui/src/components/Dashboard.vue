<script setup>
import { ref, onMounted } from 'vue'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js'
import { Line, Bar } from 'vue-chartjs'
import axios from 'axios'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend
)

const events = ref([])
const deliveries = ref([])
const stats = ref({
  totalEvents: 0,
  delivered: 0,
  failed: 0,
  pending: 0
})

const chartData = ref({
  labels: [],
  datasets: []
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false
}

const fetchData = async () => {
  try {
    // In a real app, we would have an analytics endpoint.
    // Here we will fetch recent events and deliveries to calculate some stats.
    
    // Fetch last 100 events
    const eventsRes = await axios.get('/api/v1/events?size=100')
    events.value = eventsRes.data.content || []
    
    // Fetch last 100 deliveries
    // Note: The backend might not have a generic "list all deliveries" endpoint without filters, 
    // checking available APIs from context.
    // Based on README, we have /api/v1/deliveries/{id}. We might need to check if there is a list endpoint.
    // If not, we'll just stick to event stats for now or assume a list endpoint exists/create one.
    // Let's assume /api/v1/events is the main source for now.
    
    stats.value.totalEvents = events.value.length
    
    // Prepare chart data (Events per minute)
    const timeMap = {}
    events.value.forEach(e => {
      const time = new Date(e.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      timeMap[time] = (timeMap[time] || 0) + 1
    })
    
    chartData.value = {
      labels: Object.keys(timeMap).sort(),
      datasets: [
        {
          label: 'Events Received',
          backgroundColor: '#f87979',
          data: Object.keys(timeMap).sort().map(k => timeMap[k])
        }
      ]
    }
    
  } catch (e) {
    console.error('Error fetching data', e)
  }
}

onMounted(() => {
  fetchData()
  // Poll every 5 seconds
  setInterval(fetchData, 5000)
})
</script>

<template>
  <div class="dashboard">
    <h1>HookSwarm Dashboard</h1>
    
    <div class="stats-grid">
      <div class="stat-card">
        <h3>Total Events (Recent)</h3>
        <p class="stat-value">{{ stats.totalEvents }}</p>
      </div>
      <!-- Placeholders for more stats -->
      <div class="stat-card">
        <h3>System Status</h3>
        <p class="stat-value success">Operational</p>
      </div>
    </div>

    <div class="chart-container">
      <Bar :data="chartData" :options="chartOptions" v-if="chartData.labels.length > 0" />
      <div v-else class="no-data">Waiting for events...</div>
    </div>
    
    <div class="recent-events">
      <h2>Recent Events</h2>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="event in events.slice(0, 10)" :key="event.id">
            <td>{{ event.id.substring(0, 8) }}...</td>
            <td>{{ event.eventType }}</td>
            <td>{{ new Date(event.createdAt).toLocaleString() }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  text-align: center;
}

.stat-value {
  font-size: 2em;
  font-weight: bold;
  color: #2c3e50;
  margin: 10px 0 0;
}

.success {
  color: #42b983;
}

.chart-container {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  height: 400px;
  margin-bottom: 30px;
}

.recent-events {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}
</style>
