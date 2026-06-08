import axios from 'axios';
import type { ApiEnvelope } from './types';

const http = axios.create({
    baseURL: '/api/iam',
    timeout: 10000
});

export async function unwrap<T>(request: Promise<{
    data: ApiEnvelope<T>;
}>) {
    const response = await request;
    return response.data.data;
}

export default http;
