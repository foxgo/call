import axios from 'axios';

const http = axios.create({
    baseURL: '/api/iam',
    timeout: 10000
});

export default http;
